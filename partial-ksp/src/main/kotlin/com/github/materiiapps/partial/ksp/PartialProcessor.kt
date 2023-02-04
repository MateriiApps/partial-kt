package com.github.materiiapps.partial.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

internal class PartialProcessor(val codeGenerator: CodeGenerator, val version: KotlinVersion, val logger: KSPLogger) :
    SymbolProcessor {
    val partialValueClassName = ClassName("com.github.materiiapps.partial", "Partial")
    val partialInterfaceClassName = ClassName("com.github.materiiapps.partial", "Partialable")

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
            if (classDeclaration.classKind != ClassKind.CLASS)
                return

            val packageName = classDeclaration.packageName.asString()
            val className = classDeclaration.qualifiedName?.getShortName() ?: "<ERROR>"
            val partialClassName = "${className}Partial"

            FileSpec.builder(packageName, partialClassName)
                .addImport("com.github.materiiapps.partial", "getOrElse", "Partial", "Partialable")
                .addImport(classDeclaration.packageName.asString(), className)
                .addFileComment(
                    """
                    Generated partial class for [$className]
                    DO NOT EDIT MANUALLY
                    """.trimIndent()
                )
                .addType(makePartialClass(partialClassName, classDeclaration))
                .addFunction(makeToPartialFunction(partialClassName, classDeclaration))
                .addFunction(makeExtMergeFunction(partialClassName, classDeclaration))
                .build()
                .writeTo(
                    codeGenerator = codeGenerator,
                    dependencies = Dependencies(true, classDeclaration.containingFile!!)
                )
        }

        private fun makePartialClass(partialClassName: String, classDeclaration: KSClassDeclaration): TypeSpec {
            val annotations = mutableListOf<AnnotationSpec>()
            val properties = mutableListOf<PropertySpec>()
            val parameters = mutableListOf<ParameterSpec>()

            classDeclaration.annotations.forEach { annotation ->
                createAnnotation(annotation)?.let {
                    annotations.add(it)
                }
            }
            classDeclaration.primaryConstructor!!.parameters.forEach { parameter ->
                val name = parameter.name!!.asString()
                val type = partialValueClassName.parameterizedBy(parameter.type.toTypeName())

                val paramAnnotations = mutableListOf<AnnotationSpec>()

                parameter.annotations.forEach { annotation ->
                    createAnnotation(annotation)?.let {
                        paramAnnotations.add(it)
                    }
                }

                properties.add(
                    PropertySpec
                        .builder(name, type)
                        .addAnnotations(paramAnnotations)
                        .initializer(name)
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
                .addSuperinterface(partialInterfaceClassName.parameterizedBy(classDeclaration.toClassName()))
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
                    "return %T(${parameters.joinToString(postfix = "\n") { "\n        $it = $it.getOrElse { full.$it }" }})",
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
                    "return %T(${parameters.joinToString(postfix = "\n") { "\n        $it = Partial.Value($it)" }})",
                    partialClass
                )
                .build()
        }

        private fun createAnnotation(annotation: KSAnnotation): AnnotationSpec? {
            val declaration = annotation.annotationType.resolve().declaration

            return if (declaration.qualifiedName?.asString() != PARTIAL_ANNOTATION_IDENTIFIER) {
                AnnotationSpec.builder(
                    ClassName(
                        declaration.packageName.asString(),
                        declaration.simpleName.asString()
                    )
                ).also { builder ->
                    if (annotation.arguments.isNotEmpty()) {
                        builder.addMember(annotation.arguments.joinToString {
                            when (it.value) {
                                null -> "null"
                                is String -> "\"${it.value}\""
                                is Char -> "'${it.value}'"

                                is Boolean,
                                is Byte,
                                is Short,
                                is Int,
                                is Long,
                                is Float,
                                is Double,
                                -> it.value.toString()

                                is KSType -> {
                                    /*
                                       There's a compiler bug in <1.8 Kotlin IR which produces a
                                       "Collection contains no element matching the predicate."
                                       error if a KClass parameter is passed to an annotation.
                                    */
                                    if (version.isAtLeast(1, 8)) {
                                        (it.value as KSType).declaration.qualifiedName?.asString() + "::class"
                                    } else {
                                        logger.warn(
                                            "Cannot properly read KSType from annotation arguments in Kotlin versions <1.8. " +
                                                    "As a workaround, converting the value to string. This may or may not work."
                                        )
                                        it.value.toString() + "::class"
                                    }
                                }

                                else -> {
                                    logger.warn("Unknown annotation argument type ${it.value?.javaClass?.simpleName}, converting to string")
                                    it.value.toString()
                                }
                            }
                        })
                    }
                }.build()
            } else null
        }
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiiapps.partial.Partialize"
    }
}
