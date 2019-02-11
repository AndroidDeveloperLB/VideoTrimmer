
# VideoTrimmer

Allows to trim videos on Android, including UI.

import using [Jitpack](https://jitpack.io/#AndroidDeveloperLB/VideoTrimmer) :

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
 
 	dependencies {
	        implementation 'com.github.AndroidDeveloperLB:VideoTrimmer:#'
	}

![screenshot](https://github.com/AndroidDeveloperLB/VideoTrimmer/blob/master/screenshot.png?raw=true)

 - Code based on "[k4l-video-trimmer](https://github.com/titansgroup/k4l-video-trimmer)" library, to handle various issues on it, that I've asked about [here](https://stackoverflow.com/q/54503331/878126) and [here] .
 - Trimming is done by using "[mp4parser](https://github.com/sannies/mp4parser)" library (that was used on the original library) and on [this answer](https://stackoverflow.com/a/44653626/878126), which is based on the Gallery app of Android.
 - This library handled various issues that the original had, while also having 100% code in Kotlin.
 - At first it was a fork, but as it became very different in code, and because the original one isn't maintained anymore, I decided to create this one as a new repository.
