package de.kitshn.di

import de.kitshn.AppDatabase
import de.kitshn.getDatabaseBuilder
import de.kitshn.getDatabasePath
import de.kitshn.getRoomDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<String>(DB_FILE_PATH_QUALIFIER) { getDatabasePath(androidContext()) }
    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder(androidContext())) }
}
