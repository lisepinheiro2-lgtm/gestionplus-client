module gestionRemplacement {
	requires java.desktop;
	requires java.net.http;
	requires com.fasterxml.jackson.databind;
	requires com.formdev.flatlaf;
	requires com.formdev.flatlaf.extras;
	requires com.github.weisj.jsvg;
	requires java.prefs;
	requires java.logging;
	opens gestion.net to com.fasterxml.jackson.databind;
}
