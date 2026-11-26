plugins {
    id("st.evening.mc.prelude.build") version "1.0.1"
    id("st.evening.kt.invokecontrol") version "2.4.20-0.1.0"
}

group = "st.evening.mc.portableinventorytools"
version = "1.0.1"

preludeBuild {
    modId = "portableinventorytools"
    mod {
        constantsClass = "PitConsts"
        accessTransformerFile = "portableinventorytools_at.cfg"
    }
    minecraft {
        mcVersion = "1.12.2"
        deobf {
            mappingsChannel = "stable"
            mappingsVersion = "39"
        }
    }
    presets {
        mixin {
            enable = true
        }
        prelude {
            enable = true
        }
    }
}

repositories {
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com/")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev/")
    }
}

dependencies {
    implementation(rfg.deobf("vazkii.autoreglib:AutoRegLib:1.3-32.33"))
    implementation(rfg.deobf("curse.maven:bundles-backport-902861:6339953")) // 1.2.3
    implementation(rfg.deobf("codechicken:CodeChickenLib:1.12.2-3.2.3.358:universal"))
    implementation(rfg.deobf("cofh:CoFHCore:1.12.2-4.6.3.27:universal") as String) {
        isTransitive = false
    }
    implementation(rfg.deobf("codechicken:EnderStorage:1.12.2-2.4.6.137:universal") as String) {
        isTransitive = false
    }
    implementation(rfg.deobf("blusunrize:ImmersiveEngineering:0.12-92-559:") as String) {
        isTransitive = false
    }
    implementation(rfg.deobf("curse.maven:iron-backpacks-227049:2564573")) // 1.12.2-3.0.8-12
    implementation(rfg.deobf("curse.maven:mekanism-268560:2835175")) // 1.12.2-9.8.3.390
    implementation(rfg.deobf("curse.maven:mouse-tweaks-unofficial-461660:9330373")) // 3.2.1
    implementation(rfg.deobf("vazkii.quark:Quark:r1.6-179.6") as String) {
        isTransitive = false
    }
    implementation(rfg.deobf("curse.maven:legacy-textiles-1543091:9076252")) // 1.5
}
