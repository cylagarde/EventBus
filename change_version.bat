cd /d %0\..
@findstr /C:SNAPSHOT  pom.xml
set /p version="enter version X.Y.Z "
mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=%version%-SNAPSHOT