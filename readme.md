Automatic Generation of User Interface Layouts for Different Screen Orientations
====

Creating multiple layout alternatives for graphical user interfaces to accommodate different screen orientations and sizes for mobile devices is labor intensive.
Providing good layout alternatives can inspire developers in their design work and support them to create adaptive layouts.
In general, there is a large number of possibilities of how widgets can be rearranged.
For this reason we developed a classification method to identify and evaluate ``good'' layout alternatives automatically.

This repository contains a Android Studio plugin for automatic generation of layout alternatives for different screen orientations.
Android Studio is the official development environment for Android and has an integrated design editor for XML layout files.
With our plugin users can generate landscape and portrait layout alternatives from layout XML file.
Designers can quickly browse the top-ranked layout alternatives and edit a chosen alternative if desired.


Compilation
----

The plugin has to be compiled in the Android Studio source code. An instruction to build Android Studio can be found here:
[Building Android Studio](http://tools.android.com/build/studio)

The plugin requires the ALM jar file which can be compiled from [ALM](https://gitlab.com/czeidler/alm).

To integrate the source code into the Android Studio project please apply the ideaproject.diff patch.