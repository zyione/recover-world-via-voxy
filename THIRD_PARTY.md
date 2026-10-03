# Third-party components

This source repository does not include Minecraft binaries, decompiled Minecraft sources, Voxy binaries, private databases or recovered worlds.

- Minecraft Java 1.21.11 and official Mojang mappings are downloaded during the local build. They remain subject to Mojang/Microsoft's applicable terms. This project does not grant rights to redistribute them.
- Fabric Loom 1.14.10 and Fabric Loader 0.18.4 provide the development build environment.
- RocksDB JNI 10.2.1 provides read-only database access.
- zstd-jni 1.5.7-6 decompresses cached sections.
- Gradle 9.2.1 wrapper files are included; Gradle is licensed under Apache-2.0.
- JUnit 5.11.4 is used for tests.

The Voxy format reader was developed against SaveLoadSystem3 from Voxy commit `3eda859081bfeef786804a92645d4ba8d010ecae`, corresponding to the tested 0.2.13-alpha build. The application does not execute Voxy's normal storage or mapper repair paths.

Dependencies retain their respective licenses. Review dependency notices when changing distribution methods; the provided release task distributes project source and the Gradle wrapper, not the resolved runtime classpath.
