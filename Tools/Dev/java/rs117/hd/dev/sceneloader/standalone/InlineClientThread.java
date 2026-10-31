package rs117.hd.dev.sceneloader.standalone;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
public final class InlineClientThread extends ClientThread {
	private final List<BooleanSupplier> pending = new ArrayList<>();
	private volatile Thread owner;

	private boolean watching;
	private volatile boolean watchedFinished;

	public void ownedBy(Thread owner) {
		this.owner = owner;
	}

	public void watchNext() {
		watching = true;
		watchedFinished = false;
	}

	public boolean watchedFinished() {
		return watchedFinished;
	}

	@Override
	public void invoke(Runnable runnable) {
		invoke(() -> {
			runnable.run();
			return true;
		});
	}

	@Override
	public void invoke(BooleanSupplier work) {
		BooleanSupplier watched = watch(work);
		if (Thread.currentThread() != owner) {
			invokeLater(watched);
			return;
		}
		if (!watched.getAsBoolean())
			invokeLater(watched);
	}

	private BooleanSupplier watch(BooleanSupplier work) {
		if (!watching)
			return work;
		watching = false;
		return () -> {
			boolean finished = work.getAsBoolean();
			if (finished)
				watchedFinished = true;
			return finished;
		};
	}

	@Override
	public void invokeLater(Runnable runnable) {
		invokeLater(() -> {
			runnable.run();
			return true;
		});
	}

	@Override
	public void invokeLater(BooleanSupplier work) {
		synchronized (pending) {
			pending.add(work);
		}
	}

	@Override
	public void invokeAtTickEnd(Runnable runnable) {
		invokeLater(runnable);
	}

	public void runPending() {
		List<BooleanSupplier> due;
		synchronized (pending) {
			if (pending.isEmpty())
				return;
			due = new ArrayList<>(pending);
			pending.clear();
		}

		var again = new ArrayList<BooleanSupplier>();
		for (BooleanSupplier work : due) {
			try {
				if (!work.getAsBoolean())
					again.add(work);
			} catch (Throwable ex) {
				log.warn("Something the plugin put off went wrong", ex);
			}
		}

		if (!again.isEmpty()) {
			synchronized (pending) {
				pending.addAll(again);
			}
		}
	}
}
