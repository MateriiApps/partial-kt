package com.github.materiiapps.partial.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isDefault
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
        val symbols = resolver.getSymbolsWithAnnotation(PARTIALIZE_QUALIFIED_NAME)
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
                .find { it.annotationType.resolve().toClassName() == PARTIALIZE_CLASSNAME }!!

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
                    srcClass.getDeclaredProperties().map { property ->
                        val isRequired = property.annotations.any {
                            it.annotationType.resolve().toClassName() == REQUIRED_CLASSNAME
                        }

                        val type = if (isRequired) {
                            property.type.toTypeName()
                        } else {
                            PARTIAL_CLASSNAME.parameterizedBy(property.type.toTypeName())
                        }

                        PropertySpec.builder(
                            property.simpleName.asString(),
                            type,
                            property.modifiers.mapNotNull { it.toKModifier() }
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
                .filter { it.annotations.any { it.annotationType.resolve().toClassName() == PARTIALIZE_CLASSNAME } }
                .map {
                    val className = it.toClassName()
                    ClassName(className.packageName, "${className.simpleName}Partial")
                }
                .toList()

            val properties = mutableListOf<PropertySpec>()
            val parameters = mutableListOf<ParameterSpec>()

            classDeclaration.getDeclaredProperties().forEach { property ->
                val name = property.simpleName.asString()
                val paramAnnotations = property.annotations
                    .mapNotNull { createAnnotation(it) }
                    .toList()

                val isRequired = property.annotations.any {
                    it.annotationType.resolve().toClassName() == REQUIRED_CLASSNAME
                }

                val type = if (isRequired) {
                    property.type.toTypeName()
                } else {
                    PARTIAL_CLASSNAME.parameterizedBy(property.type.toTypeName())
                }

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
                        .apply { if (!isRequired) defaultValue("Partial.Missing") }
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
            val classProperties = classDeclaration.getDeclaredProperties()
            val properties = classDeclaration.primaryConstructor!!.parameters.map { param ->
                val name = param.name!!.asString()

                val constructorParam = classProperties.find { it.simpleName.asString() == name }!!
                val isRequired = constructorParam.annotations.any {
                    it.annotationType.resolve().toClassName() == REQUIRED_CLASSNAME
                }

                name to isRequired
            }

            val code = properties.joinToString(postfix = "\n") { (_, isRequired) ->
                "\n  %N = %N${if (!isRequired) ".getOrElse { full.%N }" else ""}"
            }

            val args = properties.flatMap { (name, isRequired) -> List(if (!isRequired) 3 else 2) { name } }
                .toList().toTypedArray()

            return FunSpec.builder("merge")
                .addModifiers(KModifier.OVERRIDE)
                .returns(className)
                .addParameter("full", className)
                .addStatement(
                    "return %T($code)",
                    className, *args
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
            val classProperties = classDeclaration.getDeclaredProperties()
            val properties = classDeclaration.primaryConstructor!!.parameters.map { param ->
                val name = param.name!!.asString()

                val constructorParam = classProperties.find { it.simpleName.asString() == name }!!
                val isRequired = constructorParam.annotations.any {
                    it.annotationType.resolve().toClassName() == REQUIRED_CLASSNAME
                }

                name to isRequired
            }

            val code = properties.joinToString(postfix = "\n") { (_, isRequired) ->
                "\n  %N = ${if (isRequired) "%N" else "Partial.Value(%N)"}"
            }

            val args = properties.flatMap { (name) -> List(2) { name } }
                .toList().toTypedArray()

            return FunSpec.builder("toPartial")
                .receiver(classDeclaration.toClassName())
                .returns(partialClass)
                .addStatement(
                    "return %T($code)",
                    partialClass, *args
                )
                .build()
        }

        // TODO: use kotlin format args instead of embedding directly
        // allows for automatic imports of KClass argument types
        private fun createAnnotation(annotation: KSAnnotation): AnnotationSpec? {
            val className = annotation.annotationType.resolve().toClassName()

            if (className == PARTIALIZE_CLASSNAME || className == REQUIRED_CLASSNAME)
                return null

            return AnnotationSpec.builder(className).also { builder ->
                if (annotation.arguments.isEmpty())
                    return@also

                for (argument in annotation.arguments) {
                    if (argument.isDefault())
                        continue

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

                        is ArrayList<*> -> (value as Iterable<*>).joinToString(
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

                    builder.addMember(argument.name?.asString() + " = " + valueToString(argument.value))
                }
            }.build()
        }
    }

    companion object {
        private const val PKG = "com.github.materiiapps.partial"

        const val PARTIALIZE_QUALIFIED_NAME = "$PKG.Partialize"
        val PARTIALIZE_CLASSNAME = ClassName(PKG, "Partialize")
        val REQUIRED_CLASSNAME = ClassName(PKG, "Required")

        val PARTIAL_CLASSNAME = ClassName(PKG, "Partial")
        val PARTIALABLE_CLASSNAME = ClassName(PKG, "Partialable")
    }
}
