package info.exascale.statix;

import java.text.ParseException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;


public class main {
	public static final String  clirev = "";  // ATTENTION: Substituted during the build
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		// Mutually exclusive group of the supervised options / input format
		final OptionGroup optspv = new OptionGroup();
		// Option(String opt, String longOpt, boolean hasArg, String description)
		// ground-truth or annotated, labeled
		optspv.addOption(new Option("g", "ground-truth", true, "The ground-truth sample (subset of the input dataset or another similar dataset with the specified type properties)"));
		optspv.addOption(new Option("b", "brief-hints", true, "Brief hits, possible values:\n'--'  - interactive hints \n'<filename" + Statix.extHints + ">'  - read from the file having the format for each line:\n<indicativity> <property> \nwhere indicativity E [0, 1]; 0 - the property has no any impact on the entity type, 1 - the property fully specifies the entity type, line comments starting with '#' are allowed. \n'-[<nopts=INF>]'  - automatic generation of the hints to the <inpfile_marks" + Statix.extHints + ">, where <marks> is the range of marks (>= 2) on supervision, which defines the indicativity precision eps=1/(marks*2): eps=0.167 for 3 marks"));  // Center of each band is eps + eps*i, delta: +/-eps, wide: 2*eps
		
		Options options = new Options();
		options.addOption("h", "help", false, "Show usage");
		// Workflow: analyze input dataset, ask to rate potentially indicative properties (that might have huge impact)
		//options.addOption("p", "supervised", true, "Supervision hint data in the format: <indicativity>\t <property>, where indicativity E [0, 1], '#' line comments are allowed.");
		options.addOptionGroup(optspv);
		options.addOption("o", "output", true, "Output file, default: <inpfile>" + Statix.extCls);
		options.addOption("n", "id-name", true, "Output map of the id names (<inpfile>.idm in tab separated format: <id>	<subject_name>), default: disabled");
		options.addOption("m", "multi-level", false, "Output type inference for multiple scales (representative clusters from all hierarchy levels) besides the macro scale (top level, root)");
		options.addOption("s", "scale", true, "Scale (resolution, gamma parameter of the clustering), -1 is automatic scale inference for each cluster, >=0 is the forced static scale (<=1 for the macro clustering); default: -1");
		options.addOption("r", "reduce", true, "Reduce similarity matrix on graph construction by non-significant relations to reduce memory consumption and speedup the clustering. Options: a - accurate, m - mean, s - severe. Recommended for large datasets");
		options.addOption("f", "filter", false, "Filter out from the resulting clusters all subjects that do not have #type property in the input dataset, used for the type inference evaluation");
		options.addOption("v", "version", false, "Show version");
		
		HelpFormatter formatter = new HelpFormatter();
		String[] argsOpt = new String[]{"args"};
		final String appusage = main.class.getCanonicalName() + " [OPTIONS...] <inputfile.rdf>";
		final String desription = "Statistical type inference in fully automatic and semi supervised modes\nOptions:";
		final String reference = "\nSee details in https://github.com/eXascaleInfolab/StaTIX";
		Statix  statix = new Statix();
		
		try {
			final CommandLine  cmd = parser.parse(options, args);
			
			// Check for the help option
			if(cmd.hasOption("h")) {
				formatter.printHelp(appusage, desription, options, reference);
				System.exit(0);
			}
			
			// Check for the version
			if(cmd.hasOption("v")) {
				// Convert <revision>(<date>)[+] to the pure revision + date
				String clirevPure = clirev;
				String clirevTime = "";
				final int ibdate = clirevPure.indexOf('(');
				if(ibdate >= 0) {
					final int iedate = clirevPure.indexOf(')');
					clirevTime = clirev.substring(ibdate + 1, iedate);
					clirevPure = clirev.substring(0, ibdate) + clirev.substring(iedate + 1);
				}
				
				System.out.println("r-" + Statix.daocRevision() + "." + clirevPure);
				System.out.println("= Client Build =\nRevision: " + clirevPure + "\nTime: " + clirevTime);
				if(!Statix.daocSwigRevision().isEmpty())
					System.out.println("SWIG revision: " + Statix.daocSwigRevision());
				System.out.println("= Library Build =\n" + Statix.daocBuildInfo());
				System.exit(0);
			}
			
			String[] files = cmd.getArgs();
			if(files.length != 1)
				throw new IllegalArgumentException("A single input dataset is expected with optimal parameters");

			// Check for the filtering option
			// ATTENTION: should be done before the input datasets reading
			boolean filteringOn = cmd.hasOption("f");
			String idMapFName = null;
			if(cmd.hasOption("n"))
				idMapFName = cmd.getOptionValue("n");
				
			if(cmd.hasOption("g")) {
				String gtDataset = cmd.getOptionValue("g");
				//System.out.println("Ground-truth file= "+gtDataset);
				statix.loadDatasets(files[0], gtDataset, filteringOn, idMapFName);
			}
			else {
				String hints = cmd.hasOption("b") ? cmd.getOptionValue("b") : null;
				// Validate hints to fail early in case of issues
				if(hints != null) {
					if(hints.isEmpty())  // '' or ""
						throw new IllegalArgumentException("The hints parameter should not be empty");
					if(!hints.startsWith("-") && !Files.isReadable(Paths.get(hints))) {
						// Note: the hints are not loaded if not required for the particular dataset
						throw new IllegalArgumentException("The hints file is not readable");
						//if(!Files.exists(hints)) {
						//	// Allow absence of the specified file showing a warning, which is useful the
						//	// batch mode for the case when the hints are not necessary for this dataset
						//	System.err.println("WARNING, switching to the non-supervised mode because the hints file does not exist: " + hints);
						//	hints = null;
						//} else
						//if(!Files.isReadable(hints))
						//	throw new IllegalArgumentException("The hints file is not readable");
					}
					if(hints != "--" && hints.length() >= 2 && Integer.parseInt(hints.substring(1)) <= 1)
						throw new IllegalArgumentException("The number of marks is too small");
				}
				statix.loadDataset(files[0], filteringOn, idMapFName, hints);
			}

			// Set output file
			String outpfile = null;
			if(cmd.hasOption("o")) {
				outpfile = cmd.getOptionValue("o");
			}
			else {
				outpfile = files[0];
				// Replace the extension to the clustering results
				outpfile = Statix.updateFileExtension(outpfile, Statix.extCls);  // Default extension for the output file
			}
			// Scale
			float scale = -1;
			if(cmd.hasOption("s")) {
				scale = Float.parseFloat(cmd.getOptionValue("s"));
				if(scale != -1 && scale < 0)
					throw new IllegalArgumentException("The scale parameter is out of the expected range");
			}
			// Reduction policy
			char reduction = 'n';  // None
			if(cmd.hasOption("r")) {
				String val = cmd.getOptionValue("r");
				if(!val.isEmpty()) {
					if(val.length() >= 2 || "ams".indexOf(val.charAt(0)) == -1)
						throw new IllegalArgumentException("The reduction parameter is out of the expected range");
					reduction = val.charAt(0);
				}
			}
			
			// Perform type inference
			statix.cluster(outpfile, scale, cmd.hasOption("m"), reduction, filteringOn);
		}
		catch (ParseException e) {  //  | IllegalArgumentException
			e.printStackTrace();
			formatter.printHelp(appusage, desription, options, reference);
			System.exit(1);
		}
	}
}
