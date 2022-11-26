package com.github.materiapps.partial.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*

internal class PartialProcessor(val codeGenerator: CodeGenerator) : SymbolProcessor {
    val partialValueClassName = ClassName("com.github.materiapps.partial", "Partial")
    val partialInterfaceClassName = ClassName("com.github.materiapps.partial", "Partialable")

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
                .addImport("com.github.materiapps.partial", "getOrElse", "Partial", "Partialable")
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
                val declaration = annotation.annotationType.resolve().declaration

                if (declaration.qualifiedName?.asString() != PARTIAL_ANNOTATION_IDENTIFIER) {
                    annotations.add(
                        AnnotationSpec.builder(ClassName(declaration.packageName.asString(), declaration.simpleName.asString()))
                            .also { builder ->
                                if (annotation.arguments.isNotEmpty()) {
                                    builder.addMember(annotation.arguments.joinToString {
                                        when (it.value) {
                                            is String -> "\"${it.value}\""
                                            is KSType -> {
                                                /* FIXME
                                                    There's a compiler bug in Kotlin IR which produces a
                                                    "Collection contains no element matching the predicate."
                                                    error if a KClass parameter is passed to an annotation.
                                                 */
                                                ""
//                            val t = value.declaration.qualifiedName?.asString() + "::class"
//                            if (t == "kotlinx.serialization.KSerializer::class") "" else t
                                            }
                                            else -> it.value.toString()
                                        }
                                    })
                                }
                            }
                            .build())
                }
            }
            classDeclaration.primaryConstructor!!.parameters.forEach {
                val name = it.name!!.asString()
                val type = partialValueClassName.parameterizedBy(it.type.toTypeName())

                properties.add(PropertySpec.builder(name, type).initializer(name).build())
                parameters.add(ParameterSpec.builder(name, type).defaultValue("Partial.Missing").build())
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
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiapps.partial.Partialize"
    }
}
