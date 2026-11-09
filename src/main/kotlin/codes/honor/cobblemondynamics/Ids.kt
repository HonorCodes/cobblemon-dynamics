package codes.honor.cobblemondynamics

import net.minecraft.resources.ResourceLocation

const val MOD_ID = "cobblemon_dynamics"

fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
