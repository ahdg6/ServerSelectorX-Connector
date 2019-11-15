package xyz.derkades.ssx_connector.commands;

import java.util.List;
import java.util.Optional;

import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import xyz.derkades.ssx_connector.Main;

public class StatusCommand implements CommandCallable {

	@Override
	public CommandResult process(final CommandSource source, final String arguments) throws CommandException {
		if (Main.lastPingTimes.isEmpty()) {
			source.sendMessage(Text.of("No data has been sent to servers"));
		} else {
			source.sendMessage(Text.of("Placeholder sender: "));
			Main.lastPingTimes.forEach((k, v) -> {
				final long ago = System.currentTimeMillis() - v;
				final String error = Main.lastPingErrors.get(k);
				if (error == null) {
					source.sendMessage(
							Text.builder(String.format("  %s: Pinging successfully. Last ping %sms ago.", k, ago))
							.color(TextColors.GREEN).build());
				} else {
					source.sendMessage(
							Text.builder(String.format("  %s: Error: %s. Last ping %sms ago.", k, error, ago))
							.color(TextColors.RED).build());
				}
			});
		}

		if (Main.lastPingTimes.isEmpty()) {
			source.sendMessage(Text.of("No player list has been retrieved"));
		} else {
			source.sendMessage(Text.of("Player retriever: "));
			Main.lastPlayerRetrieveTimes.forEach((k, v) -> {
				final long ago = System.currentTimeMillis() - v;
				final String error = Main.lastPlayerRetrieveErrors.get(k);
				if (error == null) {
					source.sendMessage(
							Text.builder(String.format("  %s: Pinging successfully. Last ping %sms ago.", k, ago))
							.color(TextColors.GREEN).build());
				} else {
					source.sendMessage(
							Text.builder(String.format("  %s: Error: %s. Last ping %sms ago.", k, error, ago))
							.color(TextColors.RED).build());
				}s
			});
		}

		return CommandResult.success();
	}

	@Override
	public List<String> getSuggestions(final CommandSource source, final String arguments, final Location<World> targetPosition)
			throws CommandException {
		return null;
	}

	@Override
	public boolean testPermission(final CommandSource source) {
		return false;
	}

	@Override
	public Optional<Text> getShortDescription(final CommandSource source) {
		return null;
	}

	@Override
	public Optional<Text> getHelp(final CommandSource source) {
		return null;
	}

	@Override
	public Text getUsage(final CommandSource source) {
		return null;
	}

}