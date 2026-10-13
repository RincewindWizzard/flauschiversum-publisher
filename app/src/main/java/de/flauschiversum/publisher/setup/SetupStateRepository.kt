package de.flauschiversum.publisher.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Was der Nutzer in der Einrichtung bereits bestätigt hat (siehe [SetupFacts]). */
data class SetupMarks(
    val confirmedAddress: String? = null,
    val registeredFor: String? = null,
    val accessVerifiedFor: String? = null,
    val cloneVerifiedFor: String? = null,
)

class SetupStateRepository(private val store: DataStore<Preferences>) {
    private object K {
        val address = stringPreferencesKey("confirmed_address")
        val registered = stringPreferencesKey("registered_for")
        val access = stringPreferencesKey("access_verified_for")
        val clone = stringPreferencesKey("clone_verified_for")
    }

    val marks: Flow<SetupMarks> = store.data.map { SetupMarks(it[K.address], it[K.registered], it[K.access], it[K.clone]) }

    suspend fun current(): SetupMarks = marks.first()

    suspend fun update(transform: (SetupMarks) -> SetupMarks) {
        val next = transform(current())
        store.edit { p ->
            fun put(k: Preferences.Key<String>, v: String?) { if (v == null) p.remove(k) else p[k] = v }
            put(K.address, next.confirmedAddress); put(K.registered, next.registeredFor)
            put(K.access, next.accessVerifiedFor); put(K.clone, next.cloneVerifiedFor)
        }
    }
}
