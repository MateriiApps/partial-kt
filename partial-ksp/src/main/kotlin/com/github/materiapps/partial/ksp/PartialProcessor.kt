package com.github.materiapps.partial.ksp

import com.github.materiapps.partial.Partial
import com.github.materiapps.partial.PartialValue
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*

internal class PartialProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
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

            val file = FileSpec.builder(packageName, partialClassName).apply {
                addImport("com.github.materiapps.partial", "getOrElse")
                addImport(classDeclaration.packageName.asString(), className)

                addFileComment(
                    """
                    Generated partial class for [$className]
                    DO NOT EDIT MANUALLY
                    """.trimIndent()
                )

                val partialClass = makePartialClass(
                    partialClassName = partialClassName,
                    classDeclaration = classDeclaration,
                )

                addType(partialClass)

                addFunction(
                    makeToPartialFunction(
                        partialClassName = partialClassName,
                        classDeclaration = classDeclaration,
                    )
                )
            }

            file.build().writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(true, classDeclaration.containingFile!!)
            )
        }

        private fun makePartialClass(partialClassName: String, classDeclaration: KSClassDeclaration): TypeSpec {
            return TypeSpec.classBuilder(partialClassName).apply {
                classDeclaration.annotations.forEach {
                    if (it.annotationType.resolve().declaration.qualifiedName?.asString() != PARTIAL_ANNOTATION_IDENTIFIER) {
                        addAnnotation(it.toAnnotationSpec())
                    }
                }

                val properties = mutableListOf<PropertySpec>()
                val parameters = mutableListOf<ParameterSpec>()

                classDeclaration.primaryConstructor!!.parameters.forEach {
                    val name = it.name!!.asString()
                    val type = PartialValue::class.asClassName().parameterizedBy(it.type.toTypeName())

                    properties.add(PropertySpec.builder(name, type).initializer(name).build())
                    parameters.add(ParameterSpec.builder(name, type).defaultValue("PartialValue.Missing").build())
                }

                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(parameters)
                        .build()
                )

                addProperties(properties)

                addSuperinterface(Partial::class.asClassName().parameterizedBy(classDeclaration.toClassName()))

                addFunction(makeMergeFunction(classDeclaration))
            }.build()
        }

        private fun makeMergeFunction(classDeclaration: KSClassDeclaration): FunSpec {
            val className = classDeclaration.toClassName()
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("merge")
                .returns(className)
                .addParameter("full", className)
                .addCode("""
                    return %T(
                        ${parameters.joinToString(separator = ",\n") { "$it = $it.getOrElse { full.$it }" }}
                    )
                """.trimIndent(), className)
                .addModifiers(KModifier.OVERRIDE)
                .build()
        }

        private fun makeToPartialFunction(partialClassName: String, classDeclaration: KSClassDeclaration): FunSpec {
            val partialClass = ClassName(classDeclaration.packageName.asString(), partialClassName)
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("toPartial")
                .receiver(classDeclaration.toClassName())
                .returns(partialClass)
                .addCode("""
                    return %T(
                        ${parameters.joinToString(separator = ",\n") { "$it = PartialValue.Value($it)" }}
                    )
                """.trimIndent(), partialClass)
                .build()
        }
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiapps.partial.Partialize"
    }
}
