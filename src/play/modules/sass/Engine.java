package play.modules.sass;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jruby.embed.ScriptingContainer;
import play.Play;
import play.Logger;
import play.libs.IO;
import play.vfs.VirtualFile;

/**
 * SASS wrapper with pretty cool auto-reloading and errors reporting
 * 
 * @author guillaume bort
 */
public class Engine {

	ScriptingContainer scriptingContainer;
	Pattern extractLog = Pattern.compile("([a-zA-Z_0-9-]+[.](sass|scss):\\d+:.+)$",
			Pattern.MULTILINE);
	Pattern extractLog2 = Pattern.compile("([(]sass[)]:\\d+:.+)$",
			Pattern.MULTILINE);
	StringWriter errors = new StringWriter();
	List<String> sassPaths;
	
	Engine(File root) {
		List<String> loadPaths = new ArrayList<String>();
		loadPaths.add(new File(root, "lib").getAbsolutePath());
		for (VirtualFile vf : Play.roots) {
			loadPaths.add(new File(vf.getRealFile(), "public/stylesheets")
					.getAbsolutePath());
		}
		scriptingContainer = new ScriptingContainer();
		scriptingContainer.getProvider().setLoadPaths(loadPaths);
		scriptingContainer.setErrorWriter(errors);
	}

	public String compile(File css, boolean dev) {
		// Cache ?
		CachedCSS cachedCSS = cache.get(css);
		if (cachedCSS != null) {
			if (!dev || cachedCSS.isStillValid()) {
				return cachedCSS.css;
			}
		}

		// Paths
		sassPaths = new ArrayList<String>();
		sassPaths.add(Play.getFile("public/stylesheets").getAbsolutePath());
		StringBuffer extensions = new StringBuffer();
		for (VirtualFile vf : Play.modules.values()) {
			File style = new File(vf.getRealFile(), "public/stylesheets");
			sassPaths.add(style.getAbsolutePath());
			if (style.exists()) {
				for (File f : style.listFiles()) {
					if (f.isFile() && f.getName().endsWith(".rb")) {
						extensions.append("require '"
								+ f.getName().subSequence(0,
										f.getName().length() - 3) + "'\n");
					}
				}
			}
		}

		// Compute dependencies
		List<File> dependencies = new ArrayList<File>();
		findDependencies(css, dependencies);

		// Compile
		synchronized (Engine.class) {

			StringBuffer result = new StringBuffer();
			errors.getBuffer().setLength(0);
			scriptingContainer.put("@result", result);
			StringBuffer pathsToLoad = new StringBuffer("[");
			for (int i = 0; i < sassPaths.size(); i++) {
				pathsToLoad.append("'");
				pathsToLoad.append(sassPaths.get(i));
				pathsToLoad.append("'");
				if (i < sassPaths.size() - 1) {
					pathsToLoad.append(",");
				}
			}
			pathsToLoad.append("]");
			
			String syntax = css.getName().endsWith(".scss") ? ":scss" : ":sass";
			
			try {
				scriptingContainer
						.runScriptlet(script(
								"require 'sass'",
								extensions.toString(),
								"options = {}",
								"options[:load_paths] = " + pathsToLoad,
								"options[:syntax] = " + syntax,
								"options[:style] = "
										+ (dev ? ":expanded" : ":compressed")
										+ "",
								"options[:line_comments] = "
										+ (dev ? "true" : "false") + "",
								"input = File.new('" + css.getAbsolutePath()
										+ "', 'r')",
								"tree = ::Sass::Engine.new(input.read(), options).to_tree",
								"@result.append(tree.render)"));
			} catch (Exception e) {
				// Log ?
				String error = "";
				Matcher matcher = extractLog.matcher(errors.toString());
				while (matcher.find()) {
					error = matcher.group(1);
					Logger.error(error);
				}
				matcher = extractLog2.matcher(errors.toString());
				while (matcher.find()) {
					error = matcher.group(1).replace("(sass)", css.getName());
					Logger.error(error);
				}
				if (error.equals("")) {
					Logger.error(e, "SASS Error");
					error = "Check logs";
				}
				return "/** The CSS was not generated because the "
						+ css.getName()
						+ " file has errors; check logs **/\n\n"
						+ "body:before {display: block; color: #c00; white-space: pre; font-family: monospace; background: #FDD9E1; border-top: 1px solid pink; border-bottom: 1px solid pink; padding: 10px; content: \"[SASS ERROR] "
						+ error.replace("\"", "'") + "\"; }";
			}

			cachedCSS = new CachedCSS(result.toString(), dependencies);
			cache.put(css, cachedCSS);

			return cachedCSS.css;
		}
	}

	private String script(String... lines) {
		StringBuffer buffer = new StringBuffer();
		for (String line : lines) {
			buffer.append(line);
			buffer.append("\n");
		}
		return buffer.toString();
	}

	Pattern imports = Pattern.compile("@import\\s+\"([^\\s]+)\";");

	private void findDependencies(File sass, List<File> all) {
		try {
			if (sass.exists()) {
				all.add(sass);
				Matcher m = imports.matcher(IO.readContentAsString(sass));
				while (m.find()) {
					String fileName = m.group(1);
					for (String path : sassPaths) {
						File depImport = findImport(path, fileName);
						if (depImport != null) {
							findDependencies(depImport, all);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			Logger.error(e, "in SASS.findDependencies");
		}
	}

	final String[] sassExtensions = {"", ".sass", ".scss"};
	final String[] sassPrefixes = {"", "_"};
	
	private File findImport(String path, String fileName) {
		File unalteredImport = new File(path + "/" + fileName);
		
		for (String ext : sassExtensions) {
			for (String prefix : sassPrefixes) {
				File sassImport = new File(unalteredImport.getParentFile() + "/" + prefix + unalteredImport.getName() + ext);
				if (sassImport.exists() && sassImport.isFile()) {
					return sassImport;
				}
			}
		}
		return null;
	}

	Map<File, CachedCSS> cache = new HashMap<File, CachedCSS>();

	static class CachedCSS {

		List<File> deps;
		long ts = System.currentTimeMillis();
		String css;

		public CachedCSS(String css, List<File> deps) {
			this.css = css;
			this.deps = deps;
		}

		public boolean isStillValid() {
			for (File f : deps) {
				if (f.lastModified() > ts) {
					return false;
				}
			}
			return true;
		}
	}
}
