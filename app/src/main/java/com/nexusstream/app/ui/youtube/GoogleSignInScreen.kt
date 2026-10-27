package com.nexusstream.app.ui.youtube

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.GoogleUserProfile
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.OriginalColors

/**
 * Native Google Sign-In screen leveraging the Android on-device Google Play Services
 * account chooser dialog. Completely avoids embedded WebViews so Google cannot block sign-in.
 */
@Composable
fun GoogleSignInScreen(nav: YouTubeNav) {
    val context = LocalContext.current
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val userProfile by GoogleAccount.userProfile.collectAsState()
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember(context) { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val profile = GoogleUserProfile(
                    email = account.email.orEmpty(),
                    displayName = account.displayName ?: account.email.orEmpty().substringBefore('@'),
                    avatarUrl = account.photoUrl?.toString().orEmpty(),
                    id = account.id.orEmpty()
                )
                GoogleAccount.signInWithGoogleAccount(profile)
                nav.back()
            }
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Could not sign in with selected account"
            // If user simply cancelled, don't show an intrusive error
            if (!message.contains("12501") && !message.contains("16")) {
                errorMessage = message
            }
        }
    }

    BackHandler { nav.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = nav.back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = OriginalColors.TextPrimary)
            }
            Text(
                text = "Google Account",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OriginalColors.TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (signedIn && userProfile != null) {
                // Logged-in State Card
                val profile = userProfile!!
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(OriginalColors.Chip),
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = profile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = OriginalColors.TextPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OriginalColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Signed In",
                        tint = Color(0xFF3EA6FF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = profile.email,
                    fontSize = 14.sp,
                    color = OriginalColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        isSigningIn = true
                        googleSignInClient.signOut().addOnCompleteListener {
                            signInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OriginalColors.Red),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch Google Account", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        GoogleAccount.signOut(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF6B6B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign out", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Signed-out State
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(OriginalColors.Chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = OriginalColors.TextPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Sign in with Google",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Use any Google account already connected to this Android phone. No passwords or web browsers needed.",
                    fontSize = 14.sp,
                    color = OriginalColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = err,
                        fontSize = 13.sp,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Button(
                    onClick = {
                        isSigningIn = true
                        errorMessage = null
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "Choose Google Account",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
