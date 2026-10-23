package net.hatpack.gavel.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.hatpack.gavel.Durations;
import net.hatpack.gavel.GavelPlugin;
import net.hatpack.gavel.Msg;
import net.hatpack.gavel.PunishType;
import net.hatpack.gavel.Target;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** the native 1.21.6+ popups, used for typing custom reasons and for "are you sure" */
public final class Dialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(10))
            .build();

    private Dialogs() {
    }

    public static void custom(GavelPlugin plugin, Player staff, PunishType type, Target target, boolean silent) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("reason", Msg.parse("<gray>Reason"))
                .width(320)
                .maxLength(200)
                .build());
        if (type.timed()) {
            inputs.add(DialogInput.text("duration", Msg.parse("<gray>Duration <dark_gray>(30m, 2h, 7d, 1mo, perm)"))
                    .width(320)
                    .maxLength(24)
                    .initial(type == PunishType.MUTE ? "1h" : "1d")
                    .build());
        }
        inputs.add(DialogInput.bool("silent", Msg.parse("<gray>Silent (only staff see it)")).initial(silent).build());

        DialogActionCallback onConfirm = (view, audience) -> {
            String reason = view.getText("reason");
            String rawDuration = type.timed() ? view.getText("duration") : null;
            boolean quiet = Boolean.TRUE.equals(view.getBoolean("silent"));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (reason == null || reason.isBlank()) {
                    Msg.send(staff, "need-reason");
                    return;
                }
                long duration = Durations.PERMANENT;
                if (type.timed()) {
                    Long parsed = Durations.parse(rawDuration);
                    if (parsed == null) {
                        Msg.send(staff, "bad-duration", "input", rawDuration == null ? "" : rawDuration);
                        return;
                    }
                    duration = parsed;
                }
                plugin.punisher().punish(staff, staff.getName(), type, target, reason.trim(), duration, quiet);
            });
        };

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Msg.get("custom-title", "type", type.display(), "player", target.name()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        button("<green>" + type.display(), DialogAction.customClick(onConfirm, ONCE)),
                        button("<gray>Cancel", null))));
        staff.closeInventory();
        staff.showDialog(dialog);
    }

    public static void confirm(GavelPlugin plugin, Player staff, PunishType type, Target target,
                               String reason, long duration, boolean silent) {
        String shown = !type.timed() ? "permanent" : duration == Durations.PERMANENT ? "permanent" : Durations.format(duration);
        Component body = Msg.get("confirm-body", "type", type.display(), "player", target.name(), "reason", reason, "duration", shown);

        DialogActionCallback onYes = (view, audience) -> Bukkit.getScheduler().runTask(plugin, () ->
                plugin.punisher().punish(staff, staff.getName(), type, target, reason, duration, silent));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Msg.get("confirm-title", "type", type.display()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(body, 300)))
                        .build())
                .type(DialogType.confirmation(
                        button("<red>" + type.display(), DialogAction.customClick(onYes, ONCE)),
                        button("<gray>Cancel", null))));
        staff.closeInventory();
        staff.showDialog(dialog);
    }

    private static ActionButton button(String label, DialogAction action) {
        ActionButton.Builder b = ActionButton.builder(Msg.parse(label)).width(120);
        if (action != null) b.action(action);
        return b.build();
    }
}
