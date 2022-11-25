package com.github.materiapps.partial

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.xinto.ksputil.*
import java.io.OutputStream

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
            println(data)
            if (classDeclaration.classKind != ClassKind.CLASS)
                return

            val constructorParams = classDeclaration.primaryConstructor?.parameters
                ?: return

            val packageName = classDeclaration.packageName.asString()
            val classShortName = classDeclaration.qualifiedName?.getShortName() ?: "<ERROR>"
            val classQualifiedName = classDeclaration.qualifiedName?.asString() ?: "<ERROR>"

            val partialClassShortName = classDeclaration.simpleName.asString() + "Partial"

            val imports = mutableListOf(
                classQualifiedName,
                "com.github.materiapps.partial.PartialValue",
                "com.github.materiapps.partial.getOrElse",
            )
            val classAnnotations = classDeclaration.annotations
                .mapNotNull { annotation ->
                    annotation.sourceAnnotation().let { (type, import) ->
                        import?.also { imports.add(it) }

                        if (import == PARTIAL_ANNOTATION_IDENTIFIER)
                            return@let null

                        type
                    }
                }.toList()

            val dependencies = Dependencies(true, classDeclaration.containingFile!!)

            val transformedConstructorParams = constructorParams.map { parameter ->
                val name = parameter.name?.asString() ?: "<ERROR>"
                val type = parameter.type.sourceType().let { (type, import) ->
                    imports.addAll(import.filterNotNull())
                    type
                }

                val annotations = parameter.annotations
                    .map { annotation ->
                        annotation.sourceAnnotation().let { (type, import) ->
                            import?.also { imports.add(it) }
                            type
                        }
                    }.toList()

                Triple(name, type, annotations)
            }

            environment.codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = packageName,
                fileName = partialClassShortName
            ).use { file ->
                file.writePackage(packageName)

                file.writeImports(imports)

                file.writeWarning(
                    thing = "Partial class",
                    target = classShortName,
                )

                file.writeDataClass(
                    partialClassName = partialClassShortName,
                    classAnnotations = classAnnotations,
                    constructorParams = transformedConstructorParams
                )

                file.writeMergeFunction(
                    classShortName = classShortName,
                    partialClassShortName = partialClassShortName,
                    constructorParams = transformedConstructorParams
                )

                file.writeToPartialFunction(
                    classShortName = classShortName,
                    partialClassShortName = partialClassShortName,
                    constructorParams = transformedConstructorParams,
                )
            }
        }

        private fun OutputStream.writeDataClass(
            partialClassName: String,
            classAnnotations: List<String>,
            constructorParams: List<Triple<String, String, List<String>>>
        ) {
            classAnnotations.forEach { annotation ->
                appendTextNewline(annotation)
            }

            appendTextSpaced("data class")
            appendText(partialClassName)
            appendTextNewline("(")
            constructorParams.forEach { (name, type, annotations) ->
                annotations.forEach { annotation ->
                    withIndent {
                        appendTextNewline(annotation)
                    }
                }
                withIndent {
                    appendTextSpaced("val")
                    appendText(name)
                    appendText(": PartialValue<")
                    appendText(type)
                    appendTextNewline("> = PartialValue.Missing,")
                }
            }
            appendTextDoubleNewline(")")
        }

        private fun OutputStream.writeMergeFunction(
            classShortName: String,
            partialClassShortName: String,
            constructorParams: List<Triple<String, String, List<String>>>
        ) {
            appendTextSpaced("fun")
            appendText(classShortName)
            appendText(".")
            appendTextSpaced("merge(partial:")
            appendText(partialClassShortName)
            appendTextSpaced("):")
            appendTextSpaced(classShortName)
            appendTextNewline("{")
            withIndent {
                appendTextSpaced("return")
                appendText(classShortName)
                appendTextNewline("(")
                constructorParams.forEach { (name, _, _) ->
                    withIndent(2) {
                        appendTextSpaced(name)
                        appendTextSpaced("=")
                        appendText("partial.")
                        appendText(name)
                        appendTextSpaced(".getOrElse {")
                        appendTextSpaced(name)
                        appendText("}")
                        appendTextNewline(",")
                    }
                }
            }
            withIndent {
                appendTextNewline(")")
            }
            appendTextDoubleNewline("}")
        }

        private fun OutputStream.writeToPartialFunction(
            classShortName: String,
            partialClassShortName: String,
            constructorParams: List<Triple<String, String, List<String>>>,
        ) {
            appendTextSpaced("fun")
            appendText(classShortName)
            appendTextSpaced(".toPartial():")
            appendTextSpaced(partialClassShortName)
            appendTextNewline("{")
            withIndent {
                appendTextSpaced("return")
                appendText(partialClassShortName)
                appendTextNewline("(")
                constructorParams.forEach { (name, _, _) ->
                    withIndent(2) {
                        appendTextSpaced(name)
                        appendTextSpaced("=")
                        appendText("PartialValue.Value(")
                        appendText(name)
                        appendTextNewline("),")
                    }
                }
            }
            withIndent {
                appendTextNewline(")")
            }
            appendTextDoubleNewline("}")
        }
    }

    companion object {
        const val PARTIAL_ANNOTATION_IDENTIFIER = "com.github.materiapps.partial.Partialize"
    }
}
