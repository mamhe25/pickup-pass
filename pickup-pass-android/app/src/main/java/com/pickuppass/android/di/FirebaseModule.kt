package com.pickuppass.android.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * No FirebaseStorage binding here on purpose. As of Feb 3, 2026, Cloud
 * Storage for Firebase requires the pay-as-you-go Blaze plan even for
 * entirely free-tier usage. Avatars/logos are stored as base64 data URIs
 * directly in Firestore instead — see ProfileRepository and SchoolRepository.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
