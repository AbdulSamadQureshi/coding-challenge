package com.bonial.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class SharedPrefsManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val sharedPreferences: SharedPreferences = context.getSharedPreferences("BonialPreferences", Context.MODE_PRIVATE)
        private val gson = Gson()

        /**
         * Stores String value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: String?,
        ) {
            sharedPreferences.edit(commit = true) { putString(key, value) }
        }

        /**
         * Stores int value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: Int,
        ) {
            sharedPreferences.edit(commit = true) { putInt(key, value) }
        }

        /**
         * Stores Float value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: Float,
        ) {
            sharedPreferences.edit(commit = true) { putFloat(key, value) }
        }

        /**
         * Stores Double value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: Double,
        ) {
            sharedPreferences.edit(commit = true) { putFloat(key, value.toFloat()) }
        }

        /**
         * Stores long value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: Long,
        ) {
            sharedPreferences.edit(commit = true) { putLong(key, value) }
        }

        /**
         * Stores boolean value in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun setValue(
            key: String,
            value: Boolean,
        ) {
            sharedPreferences.edit(commit = true) { putBoolean(key, value) }
        }

        /**
         * Retrieves String value from preference
         *
         * @param key          key of preference
         * @param defaultValue default value if no key found
         */
        fun getStringValue(
            key: String,
            defaultValue: String?,
        ): String? = sharedPreferences.getString(key, defaultValue)

        /**
         * Retrieves int value from preference
         *
         * @param key          key of preference
         * @param defaultValue default value if no key found
         */
        fun getIntValue(
            key: String,
            defaultValue: Int,
        ): Int = sharedPreferences.getInt(key, defaultValue)

        /**
         * Retrieves long value from preference
         *
         * @param key          key of preference
         * @param defaultValue default value if no key found
         */
        fun getLongValue(
            key: String,
            defaultValue: Long,
        ): Long = sharedPreferences.getLong(key, defaultValue)

        /**
         * Retrieves Double value from preference
         *
         * @param key          key of preference
         * @param defaultValue default value if no key found
         */
        fun getDoubleValue(
            key: String,
            defaultValue: Double,
        ): Double = sharedPreferences.getFloat(key, defaultValue.toFloat()).toDouble()

        /**
         * Retrieves boolean value from preference
         *
         * @param key key of preference
         * @param defaultValue default value if no key found
         */
        fun getBooleanValue(
            key: String,
            defaultValue: Boolean,
        ): Boolean = sharedPreferences.getBoolean(key, defaultValue)

        /**
         * Stores object in preference
         *
         * @param key   key of preference
         * @param value value for that key
         */
        fun <T> saveObject(
            key: String,
            value: T,
        ) {
            val json = gson.toJson(value)
            sharedPreferences.edit(commit = true) { putString(key, json) }
        }

        /**
         * Retrieves object from preference
         *
         * @param key   key of preference
         * @param clazz class of object
         */
        fun <T> getObject(
            key: String,
            clazz: Class<T>,
        ): T? {
            val json = sharedPreferences.getString(key, null)
            return gson.fromJson(json, clazz)
        }

        /**
         * Removes key from preference
         *
         * @param key key of preference that is to be deleted
         */
        fun removeKey(key: String) {
            sharedPreferences.edit(commit = true) { remove(key) }
        }

        /**
         * Clears all the preferences stored
         */
        fun clear() {
            sharedPreferences.edit(commit = true) { clear() }
        }
    }
