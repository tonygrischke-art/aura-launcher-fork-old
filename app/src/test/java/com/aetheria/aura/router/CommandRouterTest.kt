package com.aetheria.aura.router

import com.aetheria.aura.router.AuraActionRoute.AskHermes
import com.aetheria.aura.router.AuraActionRoute.CallContact
import com.aetheria.aura.router.AuraActionRoute.IdentityQuery
import com.aetheria.aura.router.AuraActionRoute.OpenApp
import com.aetheria.aura.router.AuraActionRoute.SendText
import com.aetheria.aura.router.AuraActionRoute.WebSearch
import com.aetheria.aura.router.AuraActionRoute.ShizukuShell
import com.aetheria.aura.router.CommandRouter.route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterTest {

    @Test
    fun `open Spotify routes to OpenApp`() {
        val route = route("open Spotify")
        assertTrue(route is OpenApp)
        assertEquals("Spotify", (route as OpenApp).target)
    }

    @Test
    fun `open the camera routes to OpenApp`() {
        val route = route("open the camera")
        assertTrue(route is OpenApp)
        assertEquals("the camera", (route as OpenApp).target)
    }

    @Test
    fun `launch Chrome routes to OpenApp`() {
        val route = route("launch Chrome")
        assertTrue(route is OpenApp)
        assertEquals("Chrome", (route as OpenApp).target)
    }

    @Test
    fun `call Alisha routes to CallContact`() {
        val route = route("call Alisha")
        assertTrue(route is CallContact)
        assertEquals("Alisha", (route as CallContact).name)
    }

    @Test
    fun `text Alisha I'm running late routes to SendText`() {
        val route = route("text Alisha I'm running late")
        assertTrue(route is SendText)
        assertEquals("Alisha", (route as SendText).name)
        assertEquals("I'm running late", (route as SendText).body)
    }

    @Test
    fun `search for pizza near me routes to WebSearch`() {
        val route = route("search for pizza near me")
        assertTrue(route is WebSearch)
        assertEquals("for pizza near me", (route as WebSearch).query)
    }

    @Test
    fun `hello routes to AskHermes`() {
        val route = route("hello")
        assertTrue(route is AskHermes)
        assertEquals("hello", (route as AskHermes).input)
    }

    @Test
    fun `tell me a joke routes to AskHermes`() {
        val route = route("tell me a joke")
        assertTrue(route is AskHermes)
        assertEquals("tell me a joke", (route as AskHermes).input)
    }

    @Test
    fun `what is the weather routes to WebSearch`() {
        val route = route("what is the weather")
        assertTrue(route is WebSearch)
        assertEquals("the weather", (route as WebSearch).query)
    }

    @Test
    fun `build the app routes to AskHermes`() {
        val route = route("build the app")
        assertTrue(route is AskHermes)
        assertEquals("build the app", (route as AskHermes).input)
    }

    @Test
    fun `run Calculator routes to OpenApp`() {
        val route = route("run Calculator")
        assertTrue(route is OpenApp)
        assertEquals("Calculator", (route as OpenApp).target)
    }

    @Test
    fun `start Maps routes to OpenApp`() {
        val route = route("start Maps")
        assertTrue(route is OpenApp)
        assertEquals("Maps", (route as OpenApp).target)
    }

    @Test
    fun `phone Mom routes to CallContact`() {
        val route = route("phone Mom")
        assertTrue(route is CallContact)
        assertEquals("Mom", (route as CallContact).name)
    }

    @Test
    fun `dial 911 routes to CallContact`() {
        val route = route("dial 911")
        assertTrue(route is CallContact)
        assertEquals("911", (route as CallContact).name)
    }

    @Test
    fun `message Bob Hello there routes to SendText`() {
        val route = route("message Bob Hello there")
        assertTrue(route is SendText)
        assertEquals("Bob", (route as SendText).name)
        assertEquals("Hello there", (route as SendText).body)
    }

    @Test
    fun `send John meet at 5 routes to SendText`() {
        val route = route("send John meet at 5")
        assertTrue(route is SendText)
        assertEquals("John", (route as SendText).name)
        assertEquals("meet at 5", (route as SendText).body)
    }

    @Test
    fun `google kotlin coroutines routes to WebSearch`() {
        val route = route("google kotlin coroutines")
        assertTrue(route is WebSearch)
        assertEquals("kotlin coroutines", (route as WebSearch).query)
    }

    @Test
    fun `lookup weather today routes to WebSearch`() {
        val route = route("lookup weather today")
        assertTrue(route is WebSearch)
        assertEquals("weather today", (route as WebSearch).query)
    }

    @Test
    fun `empty input routes to AskHermes`() {
        val route = route("")
        assertTrue(route is AskHermes)
        assertEquals("", (route as AskHermes).input)
    }

    @Test
    fun `whitespace only routes to AskHermes`() {
        val route = route("   ")
        assertTrue(route is AskHermes)
        assertEquals("   ", (route as AskHermes).input)
    }

    @Test
    fun `open with no target routes to AskHermes`() {
        val route = route("open")
        assertTrue(route is AskHermes)
        assertEquals("open", (route as AskHermes).input)
    }

    @Test
    fun `case insensitive verb matching`() {
        val route = route("OPEN Spotify")
        assertTrue(route is OpenApp)
        assertEquals("Spotify", (route as OpenApp).target)
    }

    @Test
    fun `mixed case verb matching`() {
        val route = route("Launch Chrome")
        assertTrue(route is OpenApp)
        assertEquals("Chrome", (route as OpenApp).target)
    }

    @Test
    fun `weather exact phrase routes to WebSearch`() {
        val route = route("weather")
        assertTrue(route is WebSearch)
        assertEquals("", (route as WebSearch).query) // "weather" as first word with no query
    }

    @Test
    fun `what should I build next routes to AskHermes`() {
        val route = route("what should I build next")
        assertTrue(route is AskHermes)
        assertEquals("what should I build next", (route as AskHermes).input)
    }

    @Test
    fun `what do you think routes to AskHermes`() {
        val route = route("what do you think")
        assertTrue(route is AskHermes)
        assertEquals("what do you think", (route as AskHermes).input)
    }

    @Test
    fun `what time is it routes to AskHermes`() {
        val route = route("what time is it")
        assertTrue(route is AskHermes)
        assertEquals("what time is it", (route as AskHermes).input)
    }

    @Test
    fun `search for best pizza routes to WebSearch`() {
        val route = route("search for best pizza")
        assertTrue(route is WebSearch)
        assertEquals("for best pizza", (route as WebSearch).query)
    }

    @Test
    fun `find nearest coffee routes to WebSearch`() {
        val route = route("find nearest coffee")
        assertTrue(route is WebSearch)
        assertEquals("nearest coffee", (route as WebSearch).query)
    }

    @Test
    fun `google android 14 routes to WebSearch`() {
        val route = route("google android 14")
        assertTrue(route is WebSearch)
        assertEquals("android 14", (route as WebSearch).query)
    }

    @Test
    fun `text Alisha hello routes to SendText with body`() {
        val route = route("text Alisha hello")
        assertTrue(route is SendText)
        assertEquals("Alisha", (route as SendText).name)
        assertEquals("hello", (route as SendText).body)
    }

    @Test
    fun `text Zzzxq routes to SendText unknown contact`() {
        val route = route("text Zzzxq")
        assertTrue(route is SendText)
        assertEquals("Zzzxq", (route as SendText).name)
        assertEquals("", (route as SendText).body)
    }

    @Test
    fun `text Alisha no body routes to SendText`() {
        val route = route("text Alisha")
        assertTrue(route is SendText)
        assertEquals("Alisha", (route as SendText).name)
        assertEquals("", (route as SendText).body)
    }

    @Test
    fun `Text ALISHA hi case insensitive verb routes to SendText`() {
        val route = route("Text ALISHA hi")
        assertTrue(route is SendText)
        assertEquals("ALISHA", (route as SendText).name)
        assertEquals("hi", (route as SendText).body)
    }

    @Test
    fun `shizuku prefix id routes to ShizukuShell`() {
        val route = route("shizuku: id")
        assertTrue(route is ShizukuShell)
        assertEquals("id", (route as ShizukuShell).command)
    }

    @Test
    fun `shizuku prefix multi word command keeps rest verbatim`() {
        val route = route("shizuku: getprop ro.build.version.sdk")
        assertTrue(route is ShizukuShell)
        assertEquals("getprop ro.build.version.sdk", (route as ShizukuShell).command)
    }

    @Test
    fun `SHIZUKU prefix case insensitive routes to ShizukuShell`() {
        val route = route("SHIZUKU: pm list packages -3")
        assertTrue(route is ShizukuShell)
        assertEquals("pm list packages -3", (route as ShizukuShell).command)
    }

    @Test
    fun `shizuku prefix with no command routes to AskHermes`() {
        val route = route("shizuku:   ")
        assertTrue(route is AskHermes)
    }

    @Test
    fun `shizuku as a word without colon still routes to AskHermes`() {
        val route = route("shizuku please")
        assertTrue(route is AskHermes)
    }

    @Test
    fun `who am i routes to IdentityQuery name`() {
        val route = route("who am i")
        assertTrue(route is IdentityQuery)
        assertEquals("name", (route as IdentityQuery).queryType)
    }

    @Test
    fun `whoami routes to IdentityQuery name`() {
        val route = route("whoami")
        assertTrue(route is IdentityQuery)
        assertEquals("name", (route as IdentityQuery).queryType)
    }

    @Test
    fun `what is my name routes to IdentityQuery name`() {
        val route = route("what is my name")
        assertTrue(route is IdentityQuery)
        assertEquals("name", (route as IdentityQuery).queryType)
    }

    @Test
    fun `whats my name routes to IdentityQuery name`() {
        val route = route("whats my name")
        assertTrue(route is IdentityQuery)
        assertEquals("name", (route as IdentityQuery).queryType)
    }

    @Test
    fun `what's my name routes to IdentityQuery name`() {
        val route = route("what's my name")
        assertTrue(route is IdentityQuery)
        assertEquals("name", (route as IdentityQuery).queryType)
    }

    @Test
    fun `where am i routes to IdentityQuery location`() {
        val route = route("where am i")
        assertTrue(route is IdentityQuery)
        assertEquals("location", (route as IdentityQuery).queryType)
    }

    @Test
    fun `whereami routes to IdentityQuery location`() {
        val route = route("whereami")
        assertTrue(route is IdentityQuery)
        assertEquals("location", (route as IdentityQuery).queryType)
    }

    @Test
    fun `case insensitive identity queries`() {
        val route1 = route("WHO AM I")
        val route2 = route("What Is My Name")
        val route3 = route("WHERE AM I")
        assertTrue(route1 is IdentityQuery)
        assertTrue(route2 is IdentityQuery)
        assertTrue(route3 is IdentityQuery)
    }
}