package codes.honor.cobblemondynamics.handler.create;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.tuple.Pair;

/**
 * What {@code AirCurrentMixin} adds to Create's air current. It sits outside the mixin package, because Mixin refuses to load any
 * class of that package once its config is active.
 */
public interface AirCurrentAccess {
    /** The entities that the current caught at its last search. */
    List<Entity> cobblemon_dynamics$caughtEntities();

    /** The belts and depots under the current, each with the processing type that reaches it. */
    List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> cobblemon_dynamics$affectedHandlers();
}
