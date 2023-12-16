# Introduction

This is very much a Work-In-Progress and that'll be evident at this early stage.  

TapStrapApp (//TODO: Get a real name), Android Input Method Editor (IME) designed for use with the Tap Strap chording keyboard.
This project aims to significantly enhance the typing experience for Tap Strap users by addressing the inherent limitations of the device's firmware.

## Present Features:

   * Useable for More Than Text: Overcomes the Tap Strap's issue of typing the wrong letter followed by a backspace.
   * On-Screen IME Keyboard: Displays the current state, indicating the recently chorded fingers and held meta keys (ALT, SHIFT, CTRL, META). These indicators are also clickable for convenience.
   * Advanced Chording Feedback: Shows the output for single, double, and triple taps (and beyond), offering more depth than the standard Tap Strap behavior.  These are also clickable if you like.
   * Customizable Chording: Unlike the Tap Strap's web-based configuration, our IME allows local programming of any chord using standard CSV files, providing flexibility and extensive customization.  ANY CHORD.  You sick of several chords being reserved and un-editable in the Tap Systems Tapmapper?  NO LONGER!
   * More Taps: You want triple-taps?  Quad-taps?  There's no limit!  I just don't know what you call 5 or more.  You want SHIFT mode to do double-taps, triple taps? Have it!  You want SWITCH mode to... okay you get it.
   * More shifts/switches:  You can define as many different modes as you like and each can have infinite taps.
   * (Optional) Tap Cycling: If you keep double-triple-etc tapping, after you go past the end of what's configured, it wraps back around to single-tap again, so you can "go back" without getting the wrong output first.
   * Does All the Keys:  You know the list of like a thousand Android KeyCodes?  Well, we do!  ( See /config/DEFAULT_1 )  
   * Multiple Configuration Profiles: Supports switching between unlimited chording configurations to suit different use cases.
   * Super Untested And Unfinished Code: You won't see that from Tap Systems!

## Upcoming Features:

   * Hopefully nothing -- it would be infintely prefereable to see Tap Systems make these features available on-device, most particularly the elimination of the backspace behavior and the ability to program chords offline.  But until they do...
   * Mouse mode
   * Better UI/In-app Configuration
   * System service to make it useful even when Android doesn't want to use the IME (anytime you're not in a text entry, basically) -- work more like a hardware keyboard.
   * I'd still like to add a HID "passthrough" that allows you to connect your phone to another device and actually use the Tap Strap with these IME features on any bluetooth device.  But so far, no luck.

## IMPORTANT NOTE:

It's full of bugs and will probably set your phone on fire.  I can't take any responsibility for you choosing to use what's OBVIOUSLY unfinished code!

## Getting Started

### "Quick" Start:
* Install APK
* Run the "TapStrapIME" App and take note of the directory it tells you to put the configuration files into.
* Copy the default configuration from /config in this repository or write you own and put them in the indicated directory on your phone.  You can use any file manager, or adb, there's a script in the config.
* Go to your Android settings, find the keyboard settings (it varies) and you should find a page there where you can chose which of your installed IME/Kayboards are allowed to run.  Turn on TapStrapIME.  You can and should leave your usual keyboards enabled as well.
* (Optional but HIGHLY suggested:) If you have an option to always display the keyboard switcher (maybe "Keyboard button") make sure it's turned on.
* (Optional but suggested:) Probably on another settings screen, find the option to display the software keyboard even if a physical keyboard is attached.  
* Start up your Tap Strap, make sure it's connected to your Android device successfully.  
* Use the settings menu or the "Keyboard button" you just turned on to switch your default keyboard to the TapStrapIME.
* Find someplace to input text and try it out.  The provided config files should match the defaults in the TapStrap hardware.


## Developer's Note(s)

I absolutely love the Tap Strap, but as soon as I finished learning all the single-tap letters, I realized I'd never be able to use it as a general-purpose keyboard while it had this typing-the-wrong-character-then-backspacing behavior.

Although the best way to fix the problem would be in the Tap Strap firmware directly, Tap Systems haven't implemented an option to toggle the bizarre backspace behavior... yet.  They did provide a really nice SDK we used for this project, but it can only take you so far.

If this project eventually becomes useful to you and you'd like to show support, the developer appreciates donations to the Kitten.Academy Patreon.  https://www.patreon.com/kittenacademy

## Copyright Chris Jansen, etc, etc, you can use this for yourself, but we haven't arranged any licensing for you to modify the source or redistribute yet.

Aside from the Android boilerplate and the actual Tap Systems SDK, this is all original code so as of now the licensing is pretty much my problem.
I'm supposed to say "all rights reserved" here, right?

I'm not easy to contact but you can try scribblecj@gmail.com if you like.
