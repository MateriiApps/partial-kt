package com.github.materiiapps.partial.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

internal class PartialProcessor(
    val codeGenerator: CodeGenerator,
    val version: KotlinVersion,
    val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PARTIAL_ANNOTATION_IDENTIFIER)
        val (valid, invalid) = symbols.partition { it.validate() }

        valid
            .filterIsInstance<KSClassDeclaration>()
            .forEach {
                it.accept(PartialVisitor(), Unit)
            }

        return invalid
    }

    inner class PartialVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val packageName = classDeclaration.packageName.asString()
            val className = classDeclaration.qualifiedName?.getShortName() ?: "<ERROR>"
            val partialClassName = "${className}Partial"

            val annotation = classDeclaration.annotations
                .find { it.shortName.asString() == PARTIAL_ANNOTATION_NAME }!!

            @Suppress("UNCHECKED_CAST")
            val targetChildren = annotation.arguments
                .find { it.name?.asString() == "children" }!!
                .value as ArrayList<KSType>

            if (targetChildren.isNotEmpty()) {
                if (classDeclaration.classKind != ClassKind.INTERFACE) {
                    logger.error("Cannot generate a parent partial for a non-interface type!", classDeclaration)
                    return
                }
            } else if (classDeclaration.classKind != ClassKind.CLASS || !classDeclaration.modifiers.contains(Modifier.DATA)) {
                logger.error("Cannot generate a partial for a non data-class type!", classDeclaration)
                return
            }

            val baseFile = FileSpec.builder(packageName, partialClassName).addFileComment(
                """
                Generated file by partial-ksp class for [$className]
                DO NOT EDIT MANUALLY
                """.trimIndent()
            )

            val file = if (targetChildren.isEmpty()) {
                // Make child
                baseFile
                    .addImport("com.github.materiiapps.partial", "getOrElse", "Partial", "Partialable")
                    .addType(makePartialClass(partialClassName, classDeclaration))
                    .addFunction(makeToPartialFunction(partialClassName, classDeclaration))
                    .addFunction(makeExtMergeFunction(partialClassName, classDeclaration))
                    .build()
            } else {
                // Make parent
                baseFile
                    .addType(makeParentClass(classDeclaration, partialClassName))
                    .addFunction(
                        makeParentMergeFunction(
                            classDeclaration,
                            ClassName(packageName, partialClassName),
                            targetChildren
                        )
                    )
                    .build()
            }

            file.writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(true, classDeclaration.containingFile!!)
            )
        }

        private fun makeParentClass(srcClass: KSClassDeclaration, partialName: String): TypeSpec {
            return TypeSpec.interfaceBuilder(partialName)
                .addModifiers(srcClass.modifiers.mapNotNull { it.toKModifier() })
                .addProperties(
                    srcClass.getDeclaredProperties().map {
                        PropertySpec.builder(
                            it.simpleName.asString(),
                            PARTIAL_CLASSNAME.parameterizedBy(it.type.toTypeName()),
                            it.modifiers.mapNotNull { it.toKModifier() }
                        ).build()
                    }.toList()
                )
                .build()
        }

        private fun makeParentMergeFunction(
            parent: KSClassDeclaration,
            parentPartial: ClassName,
            targetChildren: ArrayList<KSType>,
        ): FunSpec {
            val parentClassname = parent.toClassName()

            return FunSpec.builder("merge")
                .receiver(parentClassname)
                .returns(parentClassname.copy(nullable = true))
                .addParameter("partial", parentPartial)
                .beginControlFlow("return when")
                .apply {
                    for (child in targetChildren) {
                        val className = child.toClassName()
                        val partialClassName = ClassName(className.packageName, "${className.simpleName}Partial")
                        addStatement("this is %T && partial is %T -> partial.merge(this)", className, partialClassName)
                    }
                }
                .addStatement("else -> null")
                .endControlFlow()
                .build()
        }

        private fun makePartialClass(partialClassName: String, classDeclaration: KSClassDeclaration): TypeSpec {
            val annotations = classDeclaration.annotations
                .mapNotNull { createAnnotation(it) }
                .toList()

            val partialSuperclasses = classDeclaration.superTypes
                .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                .filter { it.annotations.any { it.shortName.asString() == PARTIAL_ANNOTATION_NAME } }
                .map {
                    val className = it.toClassName()
                    ClassName(className.packageName, "${className.simpleName}Partial")
                }
                .toList()

            val properties = mutableListOf<PropertySpec>()
            val parameters = mutableListOf<ParameterSpec>()

            classDeclaration.getDeclaredProperties().forEach { property ->
                val name = property.simpleName.asString()
                val type = PARTIAL_CLASSNAME.parameterizedBy(property.type.toTypeName())
                val paramAnnotations = property.annotations
                    .mapNotNull { createAnnotation(it) }
                    .toList()

                properties.add(
                    PropertySpec
                        .builder(name, type)
                        .addAnnotations(paramAnnotations)
                        .initializer(name)
                        .addModifiers(property.modifiers.mapNotNull { it.toKModifier() })
                        .build()
                )
                parameters.add(
                    ParameterSpec
                        .builder(name, type)
                        .defaultValue("Partial.Missing")
                        .build()
                )
            }

            return TypeSpec
                .classBuilder(partialClassName)
                .addModifiers(classDeclaration.modifiers.mapNotNull { it.toKModifier() })
                .addAnnotations(annotations)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(parameters)
                        .build()
                )
                .addProperties(properties)
                .addSuperinterface(PARTIALABLE_CLASSNAME.parameterizedBy(classDeclaration.toClassName()))
                .addSuperinterfaces(partialSuperclasses)
                .addFunction(makeMergeFunction(classDeclaration))
                .build()
        }

        private fun makeMergeFunction(classDeclaration: KSClassDeclaration): FunSpec {
            val className = classDeclaration.toClassName()
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("merge")
                .addModifiers(KModifier.OVERRIDE)
                .returns(className)
                .addParameter("full", className)
                .addStatement(
                    "return %T(${parameters.joinToString(postfix = "\n") { "\n  $it = $it.getOrElse { full.$it }" }})",
                    className
                )
                .build()
        }

        private fun makeExtMergeFunction(partialClassName: String, classDeclaration: KSClassDeclaration): FunSpec {
            val className = classDeclaration.toClassName()
            val partialClass = ClassName(classDeclaration.packageName.asString(), partialClassName)

            return FunSpec.builder("merge")
                .receiver(className)
                .addParameter("partial", partialClass)
                .returns(className)
                .addStatement("return partial.merge(this)")
                .build()
        }

        private fun makeToPartialFunction(partialClassName: String, classDeclaration: KSClassDeclaration): FunSpec {
            val partialClass = ClassName(classDeclaration.packageName.asString(), partialClassName)
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("toPartial")
                .receiver(classDeclaration.toClassName())
                .returns(partialClass)
                .addStatement(
                    "return %T(${parameters.joinToString(postfix = "\n") { "\n  $it = Partial.Value($it)" }})",
                    partialClass
                )
                .build()
        }

        private fun createAnnotation(annotation: KSAnnotation): AnnotationSpec? {
            val declaration = annotation.annotationType.resolve().declaration

            if (declaration.qualifiedName?.asString() == PARTIAL_ANNOTATION_IDENTIFIER)
                return null

            return AnnotationSpec.builder(
                ClassName(
                    declaration.packageName.asString(),
                    declaration.simpleName.asString()
                )
            ).also { builder ->
                if (annotation.arguments.isNotEmpty()) {
                    builder.addMember(annotation.arguments.joinToString { argument ->
                        fun valueToString(value: Any?): CharSequence = when (value) {
                            null -> "null"
                            is String -> "\"${value}\""
                            is Char -> "'${value}'"

                            is Boolean,
                            is Byte,
                            is Short,
                            is Int,
                            is Long,
                            is Float,
                            is Double,
                            -> value.toString()

                            is ArrayList<*>,
                            is Array<*>,
                            -> (value as Iterable<*>).joinToString(
                                prefix = "[",
                                postfix = "]",
                                transform = ::valueToString,
                            )

                            is KSType -> {
                                /*
                                   There's a compiler bug in <1.8 Kotlin IR which produces a
                                   "Collection contains no element matching the predicate."
                                   error if a KClass parameter is passed to an annotation.
                                */
                                if (version.isAtLeast(1, 8)) {
                                    value.declaration.qualifiedName?.asString() + "::class"
                                } else {
                                    logger.warn(
                                        "Cannot properly read KSType from annotation arguments in Kotlin versions <1.8. " +
                                                "As a workaround, converting the value to string. This may or may not work.",
                                        symbol = argument
                                    )
                                    "$value::class"
                                }
                            }

                            else -> {
                                logger.warn(
                                    "Unknown annotation argument type ${value.javaClass.simpleName}, converting to string",
                                    symbol = argument
                                )
                                value.toString()
                            }
                        }

                        valueToString(argument.value)
                    })
                }
            }.build()
        }
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiiapps.partial.Partialize"
        const val PARTIAL_ANNOTATION_NAME = "Partialize"

        val PARTIAL_CLASSNAME = ClassName("com.github.materiiapps.partial", "Partial")
        val PARTIALABLE_CLASSNAME = ClassName("com.github.materiiapps.partial", "Partialable")
    }
}
