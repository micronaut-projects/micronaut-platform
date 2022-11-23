package io.micronaut.build.internal

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class PomExtension {
    abstract val tokenReplacements: MapProperty<String, String>
    abstract val pomProperties: MapProperty<String, String>
    abstract val includeBomPropertiesStringListProperty: ListProperty<String>
    abstract val catalogPropertyPrefix: Property<String>
}
