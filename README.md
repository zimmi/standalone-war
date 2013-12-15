standalone-war
==============

Example webapp that can be started like this:

    java -jar myapp.war

This will not use the maven-shade-plugin to mix the servlet container classes into the war as usual, but do some classloading magic at the start instead.

This is just an exercise for me, do with it what you want.
