package com.sokoul.iptv

import android.view.KeyEvent
import com.sokoul.iptv.ui.screens.player.LiveCmd
import com.sokoul.iptv.ui.screens.player.LiveKeys
import com.sokoul.iptv.ui.screens.player.LiveLists
import com.sokoul.iptv.ui.screens.player.LiveNav
import com.sokoul.iptv.ui.screens.player.LivePanels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Touches du direct : OK = détails, ◀ chaînes puis catégories, ▶ programmes, ▲▼ ne zappent que sans panneau. */
class LiveKeysTest {
    private val lists = LiveLists(channels = 50, groups = 8, programs = 20, currentChannel = 12, currentGroup = 3)
    private fun key(code: Int, nav: LiveNav = LiveNav(), repeat: Int = 0) = LiveKeys.reduce(code, repeat, nav, lists, inverted = false)!!

    @Test fun `sans panneau, haut et bas zappent`() {
        assertEquals(LiveCmd.Zap(-1), key(KeyEvent.KEYCODE_DPAD_UP).cmd)
        assertEquals(LiveCmd.Zap(1), key(KeyEvent.KEYCODE_DPAD_DOWN).cmd)
        // sens inversé dans les réglages
        assertEquals(LiveCmd.Zap(1), LiveKeys.reduce(KeyEvent.KEYCODE_DPAD_UP, 0, LiveNav(), lists, inverted = true)!!.cmd)
    }

    @Test fun `OK ouvre les details de la chaine, pas la liste`() {
        val r = key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(LivePanels(details = true), r.nav.panels)
        assertNull(r.cmd)
        assertTrue(r.hideInfo)
        // OK maintenu : ignoré
        assertEquals(LiveNav(), key(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2).nav)
    }

    @Test fun `bas dans les programmes fait defiler sans zapper`() {
        val guide = key(KeyEvent.KEYCODE_DPAD_RIGHT).nav
        assertEquals(LivePanels(guide = true), guide.panels)
        val down = key(KeyEvent.KEYCODE_DPAD_DOWN, guide)
        assertNull(down.cmd)
        assertEquals(1, down.nav.guide)
        // borné à la fin de la liste
        var n = guide
        repeat(40) { n = key(KeyEvent.KEYCODE_DPAD_DOWN, n).nav }
        assertEquals(19, n.guide)
        assertNull(key(KeyEvent.KEYCODE_DPAD_UP, n).cmd)
    }

    @Test fun `gauche ouvre les chaines sur la chaine regardee, gauche encore les categories`() {
        val list = key(KeyEvent.KEYCODE_DPAD_LEFT).nav
        assertEquals(LivePanels(list = true), list.panels)
        assertEquals(12, list.list)
        val groups = key(KeyEvent.KEYCODE_DPAD_LEFT, list).nav
        assertEquals(LivePanels(list = true, groups = true), groups.panels)
        assertEquals(3, groups.group)
        // ▲▼ parcourent les catégories, pas les chaînes
        val moved = key(KeyEvent.KEYCODE_DPAD_DOWN, groups).nav
        assertEquals(4, moved.group)
        assertEquals(12, moved.list)
        // OK ouvre la catégorie : retour à la liste, en haut
        val pick = key(KeyEvent.KEYCODE_DPAD_CENTER, moved)
        assertEquals(LiveCmd.SelectGroup(4), pick.cmd)
        assertEquals(LivePanels(list = true), pick.nav.panels)
        assertEquals(0, pick.nav.list)
    }

    @Test fun `OK dans la liste regarde la chaine et ferme`() {
        val list = key(KeyEvent.KEYCODE_DPAD_LEFT).nav
        val r = key(KeyEvent.KEYCODE_DPAD_CENTER, key(KeyEvent.KEYCODE_DPAD_DOWN, list).nav)
        assertEquals(LiveCmd.Play(13), r.cmd)
        assertEquals(LivePanels(), r.nav.panels)
    }

    @Test fun `retour ferme dans le bon ordre`() {
        val groups = LiveNav(LivePanels(list = true, groups = true))
        val afterGroups = LiveKeys.back(groups)!!
        assertEquals(LivePanels(list = true), afterGroups.panels)
        assertEquals(LivePanels(), LiveKeys.back(afterGroups)!!.panels)
        assertEquals(LivePanels(), LiveKeys.back(LiveNav(LivePanels(details = true)))!!.panels)
        assertNull(LiveKeys.back(LiveNav()))
    }

    @Test fun `CH+ et CH- zappent toujours, Menu n est pas gere ici`() {
        val guide = LiveNav(LivePanels(guide = true))
        assertEquals(LiveCmd.Zap(1), key(KeyEvent.KEYCODE_CHANNEL_UP, guide).cmd)
        assertEquals(LiveCmd.Zap(-1), key(KeyEvent.KEYCODE_CHANNEL_DOWN, guide).cmd)
        assertNull(LiveKeys.reduce(KeyEvent.KEYCODE_MENU, 0, LiveNav(), lists, false))
    }
}
