package rs117.hd.gui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link HdDevPanel} for automatic discovery by {@link HdPanelRegistry}. SIDEBAR panels
 * become a tab in the shared {@link HdDevFrameGroup}; BOTTOM/WINDOW panels each get their own
 * {@link HdDevFrame}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HdPanel
{
	/** Where this panel is presented by default. */
	DockType value();

	/** Title shown in the tab/header/window title bar. */
	String name();

	/** Whether this panel can be popped out into its own floating window. Ignored for {@link DockType#SIDEBAR}. */
	boolean canDeDock() default true;

	/** Whether {@link HdPanelRegistry} should register this panel automatically on startup. */
	boolean autoRegister() default true;
}
