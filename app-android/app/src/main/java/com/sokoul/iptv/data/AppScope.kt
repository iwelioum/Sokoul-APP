package com.sokoul.iptv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Portée applicative pour les écritures qui doivent survivre à la fermeture d'un écran (ex. dernière position de lecture). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
