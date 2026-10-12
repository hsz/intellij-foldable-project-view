package ski.chrzanow.foldableprojectview

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.openapi.observable.util.transform
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.ComponentPredicate
import ski.chrzanow.foldableprojectview.settings.FoldableProjectState
import ski.chrzanow.foldableprojectview.settings.Rule
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1

fun Cell<ColorPanel>.bindColor(graphProperty: ObservableMutableProperty<Rule?>, property: KMutableProperty1<Rule, Color?>) =
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

/**
 * Checkbox toggling an optional [property] of the edited rule: checking it sets the [defaultValue] when the property
 * is empty, unchecking it clears the property.
 */
fun <T : Any> Cell<JBCheckBox>.bindOptionalControl(graphProperty: ObservableMutableProperty<Rule?>, property: KMutableProperty1<Rule, T?>, defaultValue: T) =
    applyToComponent {
        bind(with(graphProperty) {
            transform(
                { it?.let(property::get) != null },
                { selected ->
                    get()?.apply {
                        (property.get(this) ?: defaultValue)
                            .takeIf { selected }
                            .also { property.set(this, it) }
                    }
                },
            )
        })
    }

fun <T : FoldableProjectState> Cell<JBCheckBox>.bindSelected(graphProperty: ObservableMutableProperty<T>, property: KMutableProperty1<T, Boolean>) =
    bindSelected(with(graphProperty) {
        transform(
            { it.let(property::get) },
            { value -> get().also { property.set(it, value) } }
        )
    })

fun <T : JTextComponent> Cell<T>.bindText(graphProperty: ObservableMutableProperty<Rule?>, property: KMutableProperty1<Rule, String>) =
    bindText(with(graphProperty) {
        transform(
            { it?.let(property::get).orEmpty() },
            { value -> get()?.apply { property.set(this, value) } },
        )
    })

fun <T : Any> SegmentedButton<T>.bindSelectedItem(graphProperty: ObservableMutableProperty<Rule?>, property: KMutableProperty1<Rule, T>, defaultValue: T) =
    bind(with(graphProperty) {
        transform(
            { it?.let(property::get) ?: defaultValue },
            { value -> get()?.apply { property.set(this, value) } },
        )
    })

fun <T : FoldableProjectState> ObservableMutableProperty<T>.createPredicate(property: KMutableProperty1<T, Boolean>) =
    object : ComponentPredicate() {

        private val observableProperty = transform(property)

        override fun invoke() = observableProperty.get()

        override fun addListener(listener: (Boolean) -> Unit) = observableProperty.afterChange(listener)
    }

fun <T> ObservableProperty<T>.createPredicate(predicate: (T) -> Boolean) =
    object : ComponentPredicate() {

        override fun invoke() = predicate(get())

        override fun addListener(listener: (Boolean) -> Unit) = afterChange { listener(predicate(it)) }
    }
