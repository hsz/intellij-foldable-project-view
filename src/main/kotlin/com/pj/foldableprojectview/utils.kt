package com.pj.foldableprojectview

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.observable.util.transform
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.ComponentPredicate
import com.pj.foldableprojectview.settings.FoldableProjectState
import com.pj.foldableprojectview.settings.Rule
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1

fun Cell<ColorPanel>.bindColor(
    graphProperty: ObservableMutableProperty<Rule?>,
    property: KMutableProperty1<Rule, Color?>
) =
    applyToComponent {
        with(graphProperty) {
            val mutex = AtomicBoolean()

            selectedColor = get()?.let(property::get)

            graphProperty.afterChange {
                mutex.lockOrSkip {
                    selectedColor = it?.let(property::get)
                }
            }

            addActionListener {
                mutex.lockOrSkip {
                    get()?.let {
                        property.set(it, selectedColor)
                        graphProperty.setValue(null, property, it)
                    }
                }
            }
        }
    }

fun Cell<JBCheckBox>.bindColorControl(
    graphProperty: ObservableMutableProperty<Rule?>,
    property: KMutableProperty1<Rule, Color?>,
    defaultValue: Color
) =
    applyToComponent {
        bind(with(graphProperty) {
            transform(
                { it?.let(property::get) != null },
                { selected ->
                    get()?.apply {
                        property
                            .get(this)
                            .or(defaultValue)
                            .takeIf { selected }
                            .also { property.set(this, it) }
                    }
                },
            )
        })
    }

fun <T : FoldableProjectState> Cell<JBCheckBox>.bindSelected(
    graphProperty: ObservableMutableProperty<T>,
    property: KMutableProperty1<T, Boolean>
) =
    bindSelected(with(graphProperty) {
        transform(
            { it.let(property::get) },
            { value -> get().also { property.set(it, value) } }
        )
    })

fun <T : JTextComponent> Cell<T>.bindText(
    graphProperty: ObservableMutableProperty<Rule?>,
    property: KMutableProperty1<Rule, String>
) =
    bindText(with(graphProperty) {
        transform(
            { it?.let(property::get).orEmpty() },
            { value -> get()?.apply { property.set(this, value) } },
        )
    })

fun <T : FoldableProjectState> ObservableMutableProperty<T>.createPredicate(property: KMutableProperty1<T, Boolean>) =
    object : ComponentPredicate() {

        private val observableProperty = transform(property)

        override fun invoke() = observableProperty.get()

        override fun addListener(listener: (Boolean) -> Unit) = observableProperty.afterChange(listener)
    }

fun <T> T?.or(other: T): T = this ?: other

fun <T> T?.or(block: () -> T): T = this ?: block()
