package com.github.materiapps.partial

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*

internal class PartialProcessor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {
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

            val file = FileSpec.builder(packageName, "$className.kt").apply {
                addBodyComment(
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
                    makeMergeFunction(
                        partialClassName = partialClassName,
                        classDeclaration = classDeclaration,
                    )
                )
                addFunction(
                    makeToPartialFunction(
                        partialClassName = partialClassName,
                        classDeclaration = classDeclaration,
                    )
                )
            }

            file.build().writeTo(
                codeGenerator = environment.codeGenerator,
                dependencies = Dependencies(true, classDeclaration.containingFile!!)
            )
        }

        private fun makePartialClass(partialClassName: String, classDeclaration: KSClassDeclaration): TypeSpec {
            return TypeSpec.classBuilder(partialClassName).apply {
                addAnnotations(classDeclaration.annotations
                    .map { it.toAnnotationSpec() }
                    .filterNot { it.typeName.equals(PARTIAL_ANNOTATION_IDENTIFIER) }
                    .toList()
                )

                val parameters = classDeclaration.primaryConstructor!!.parameters.map {
                    ParameterSpec.builder(
                        it.name!!.asString(),
                        Partial::class.asClassName().parameterizedBy(it.type.toTypeName()),
                        it.type.modifiers.mapNotNull { it.toKModifier() }
                    ).run {
                        defaultValue("Partial.Missing")
                        build()
                    }
                }

                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(parameters)
                        .build()
                )
            }.build()
        }

        private fun makeMergeFunction(partialClassName: String, classDeclaration: KSClassDeclaration): FunSpec {
            val className = classDeclaration.toClassName()
            val partialClass = ClassName(classDeclaration.packageName.asString(), partialClassName)
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("merge")
                .receiver(className)
                .returns(className)
                .addParameter("partial", partialClass)
                .beginControlFlow("return %T", className)
                .apply {
                    parameters.forEach {
                        addStatement("$it = partial.getOrElse { $it }")
                    }
                }
                .endControlFlow()
                .build()
        }

        private fun makeToPartialFunction(partialClassName: String, classDeclaration: KSClassDeclaration): FunSpec {
            val className = classDeclaration.toClassName()
            val partialClass = ClassName(classDeclaration.packageName.asString(), partialClassName)
            val parameters = classDeclaration.primaryConstructor!!.parameters.map { it.name!!.asString() }

            return FunSpec.builder("toPartial")
                .receiver(classDeclaration.toClassName())
                .returns(partialClass)
                .beginControlFlow("return %T", partialClass)
                .apply {
                    parameters.forEach {
                        addStatement("$it = Partial.Value($it)")
                    }
                }
                .endControlFlow()
                .build()
        }
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiapps.partial.Partialize"
    }
}
