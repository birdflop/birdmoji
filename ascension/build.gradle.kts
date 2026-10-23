dependencies {
    // DiscordSRV Ascension's API isn't published yet, this is built from https://github.com/DiscordSRV/Ascension
    // with ./gradlew :api:shadowJar (commit accd86a). It bundles its relocated copy of JDA.
    compileOnly(files("libs/discordsrv-ascension-api-3.0.0-SNAPSHOT.jar"))
}
