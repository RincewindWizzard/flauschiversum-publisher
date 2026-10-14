package de.flauschiversum.publisher

import de.flauschiversum.publisher.git.AddressParse
import de.flauschiversum.publisher.git.RepositoryAddresses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryAddressTest {
    private fun valid(text: String) = (RepositoryAddresses.parse(text) as AddressParse.Valid).address

    @Test fun vorgabeIstGueltigUndMerktEigentuemerUndName() {
        val a = valid(RepositoryAddresses.DEFAULT)
        assertEquals("git", a.user)
        assertEquals("git.magierdinge.de", a.host)
        assertEquals(2222, a.port)
        assertEquals("nadja/flauschiversum", a.path)
        assertEquals("nadja" to "flauschiversum", a.ownerAndName)
        assertEquals("ssh://git@git.magierdinge.de:2222/nadja/flauschiversum", a.gitUrl)
        assertTrue(!a.isGitHub)
    }

    @Test fun kurzformMitPort() {
        val a = valid("git@git.magierdinge.de:2222:nadja/flauschiversum")
        assertEquals("git.magierdinge.de", a.host); assertEquals(2222, a.port); assertEquals("nadja/flauschiversum", a.path)
        assertEquals("git@git.magierdinge.de:2222:nadja/flauschiversum", a.text)
        assertEquals("ssh://git@git.magierdinge.de:2222/nadja/flauschiversum", a.gitUrl)
        // JGit liest die Adresse mit dem richtigen Port, Benutzer und Pfad
        val u = org.eclipse.jgit.transport.URIish(a.gitUrl)
        assertEquals(2222, u.port); assertEquals("git.magierdinge.de", u.host); assertEquals("git", u.user); assertEquals("/nadja/flauschiversum", u.path)
    }

    @Test fun kurzformMitUngueltigemPort() {
        assertTrue(RepositoryAddresses.parse("git@host:70000:a/b") is AddressParse.Invalid)
        assertTrue(RepositoryAddresses.parse("git@host:0:a/b") is AddressParse.Invalid)
    }

    @Test fun kurzformOhnePortBleibtPortNull22() {
        val a = valid("git@host.example:2222")  // Pfad "2222", kein Port
        assertEquals(22, a.port); assertEquals("2222", a.path); assertEquals(a.text, a.gitUrl)
    }

    @Test fun eigenerServerMitPortUndPfad() {
        val a = valid("ssh://git@vps.beispiel.de:2222/srv/git/flauschiversum.git")
        assertEquals("vps.beispiel.de", a.host)
        assertEquals(2222, a.port)
        assertEquals("/srv/git/flauschiversum.git", a.path)
        assertNull(a.ownerAndName)
    }

    @Test fun kurzformMitRelativemPfad() {
        val a = valid("git@vps.beispiel.de:flauschiversum.git")
        assertEquals("vps.beispiel.de", a.host)
        assertEquals(22, a.port)
        assertEquals("flauschiversum.git", a.path)
    }

    @Test fun sshUrlOhnePortNutztStandardport() {
        assertEquals(22, valid("ssh://git@example.org/repo.git").port)
    }

    @Test fun httpsWirdErkanntUndSshAdresseVorgeschlagen() {
        val r = RepositoryAddresses.parse("https://github.com/RincewindWizzard/flauschiversum")
        assertEquals("git@github.com:RincewindWizzard/flauschiversum.git", (r as AddressParse.Https).suggestion)
        val r2 = RepositoryAddresses.parse("https://user@gitlab.example/gruppe/projekt.git/")
        assertEquals("git@gitlab.example:gruppe/projekt.git", (r2 as AddressParse.Https).suggestion)
    }

    @Test fun ungueltigeEingaben() {
        listOf("", "   ", "flauschiversum", "git@host", "ftp://x/y", "git://github.com/a/b.git", "ssh://git@host:99999/x.git")
            .forEach { assertTrue("'$it' sollte ungültig sein", RepositoryAddresses.parse(it) is AddressParse.Invalid) }
    }

    @Test fun ungueltigeMeldungNenntBeispiele() {
        val r = RepositoryAddresses.parse("flauschiversum") as AddressParse.Invalid
        assertTrue(r.reason.contains("git@github.com"))
    }

    @Test fun deployKeySeiteNurFuerGitHub() {
        assertEquals(
            "https://github.com/RincewindWizzard/flauschiversum/settings/keys/new",
            valid("git@github.com:RincewindWizzard/flauschiversum.git").gitHubDeployKeyUrl(),
        )
        assertNull(valid("git@vps.beispiel.de:flauschiversum.git").gitHubDeployKeyUrl())
    }

    @Test fun leerzeichenAmRandWerdenEntfernt() {
        assertEquals("git@github.com:a/b.git", valid("  git@github.com:a/b.git \n").text)
    }
}
