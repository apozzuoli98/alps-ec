package ec.alps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import ec.EvolutionState;
import ec.Evolve;
// import ec.fsalps.FSALPS;
// import ec.fsalps.Roulette;
import ec.alps.layers.ALPSLayers;
import ec.alps.layers.AgingScheme;
import ec.alps.layers.Layer;
import ec.util.MersenneTwisterFast;
import ec.util.Output;
import ec.util.Parameter;
import ec.util.ParameterDatabase;
import ec.util.Version;

/**
 * This class is the entry point for ALPS (it contains the main() method), Loads all
 * ALPS Properties and begins Evolution using either GenEvolutionState or SStateEvolutionState
 * This sets up all ALPS properties and begins ALPS evolution from the parameter file
 * 
 * @author Anthony Awuley
 * @version 1.0
 */
public class Engine extends Evolve {

	/** base(parent) alps parameter */
	public final static String ALPS = "alps";
	/** Returns the default base. */
	public static final Parameter base() { return new Parameter(ALPS); }
	/** */
	public static ALPSLayers alps;
	/** */
	public  static int completeGenerationalCount = 0; //changed from 1
	/** used in steady state evolution */
	public  static int completeEvaluationCount   = 0;
	/** global count of evaluations. initialized to 0 */
	public static int globalEvaluations          = 0;

	/** Should we muzzle stdout and stderr? 
	 * @deprecated */
	static final String P_MUZZLE = "muzzle";
	/**
	 * This is the same as the number of expected population in a layer 
	 * this is modified during startFresh() in steady state 
	 */
	public  static int generationSize            = 0; //
	/**
	 * Evaluation is always measured using alpsEvaluations. when generation is specified, it is converted to alpsEvaluations using
	 * numGenerations * alpsAgeLayers * generationSize;
	 * else the number of evaluations specified is used.
	 * this gives the total number of evaluations required to complete 
	 * all ALPS runs
	 */
	public  static int alpsEvaluations           = 0; //modified in code
	/** number of jobs specified from parameter file */
	public  static int numberOfJobs              = 1; 

	/** this is the total number of specified generations in parameter file */
	public static int numGenerations;

	public final static String AGING_SCHEME         = "aging-scheme";
	//public final static String ALPS_EVALUATIONS     = "alps.number-of-evaluations";

	/** number of chunks available when using k-fold cross validation */
	public  static int kFoldCrossValidationSize                      = 1; 
	/** */
	public static final String K_FOLD_CROSS_VALIDATION_CHUNCK        = "k-fold-cross-validation-size";

	/**ALPS */
	public static final String AGE_ONLY_CURRENT_LAYER                = "age-only-current-layer";
	public static final String ALWAYS_BREED_MAXIMUM_POP              = "always-breed-maximum-population";
	public static final String ASSIGN_MAX_PARENT_AGE                 = "assign-max-parent-age";
	
	/** FSALPS 
	public static final String FSALPS_USE_ONLY_DEFAULT_NODE_PR_PARAM = "use-only-default-node-pr";
	public static final String FSALPS_USE_MUTATION_PARAM             = "fsalps-in-mutation";
	public static final String FSALPS_GEN_FREQ_COUNT                 = "fsalps-gen-freq-count";
	public static final String FSALPS_COUNT_ALL_LAYERS               = "fsalps-count-all-layers";
	public static final String FSALPS_ROULETTE                       = "probability-selection";
	*/

	/** Used to keep node usage for terminal sets 
	 * stored default node settings for terminals */
	public static Map<String, Double>  nodeCountTerminalSet = new LinkedHashMap<String, Double>();
	/** Used to keep node usage for function sets 
	 * stored default node settings for non-terminals */
	public static Map<String, Double>  nodeCountFunctionSet = new LinkedHashMap<String, Double>();

	/** Use strictly default node count specified in parameter file */
	//public static boolean fsalps_use_only_default_node_pr   = false;

	/** When true, all layers node count is used in generating
	 *  probability node selection
	 *  Else only last layer node count (frequency) is converted to probabilities 
	 *  see layerFrequencySelection(ALPSLayers alps,EvolutionState state) in Roulette class for implementation*/
	//public static boolean fsalps_count_all_layers           = false;

	/** FSALPS is active */
	//public static boolean fsalps_active                     = false;
	/** Use FSALPS generated frequency count during mutation 
	 * @deprecated */
	//public static boolean fsalps_use_mutation               = true;
	/** Should frequency count be performed for every generation in the highest ALPS layer? 
	 *  if false, frequency count is performed at every age-gap interval */
	//public static boolean fsalps_gen_freq_count             = false;
    
	/**
	 * by default an offspring is assigned the age of the oldest parent + 1.
	 * if this flag is set to false, then the minimum age +1 is assigned to the offspring
	 */
	public static boolean alps_assign_max_parent_age        = true;
	/**
	 * when true, only individuals selected from breeding from current layer have their age increased
	 * else both both individuals coming from current and lower layer used as parents will have their age increased
	 */
	public static boolean alps_age_only_current_layer       = false;
	/**
	 * when using selection pressure, individual aging isn't uniform especially when parents are selected from lower
	 * layer. When some individuals are aged faster than others, a population will contain less than expected required number
	 * ECJ by default breeds a maximum of the number of populations contained in a population.
	 */
	public static boolean always_breed_maximum_pop          = true;

	/** hold rouletee values */
	//public static Roulette roulette;
	
	/**  this engine seed is to ensure that all layers are started off with a different seed */
	public static int seed  = 0;

	/** aging scheme */
	public static AgingScheme       ageScheme;
	private static ArrayList<Layer> alpsLayers;

	public Engine(String[] args,int job,ParameterDatabase parameters ) 
	{
		setup(parameters);

		alpsLayers  = ageScheme.agingScheme();

		//add a GP to each layer
		for(Layer l: alpsLayers)
		{
			if(!l.getIsBottomLayer())
				l.initializerFlag = false;
			/* should we print the help message and quit? */
			checkForHelp(args);

			/* if we're loading from checkpoint, let's finish out the most recent job */
			l.evolutionState = (EvolutionState) possiblyRestoreFromCheckpoint(args);
			l.currentJob     = 0;                             // the next job number (0 by default)

			/* this simple job iterator just uses the 'jobs' parameter, iterating from 0 to 'jobs' - 1
			 * inclusive.  The current job number is stored in state.jobs[0], so we'll begin there if
			 * we had loaded from checkpoint.
			 */
			if (l.evolutionState != null)  // loaded from checkpoint
			{
				/* extract the next job number from state.job[0] (where in this example we'll stash it) */
				try
				{
					if (l.evolutionState.runtimeArguments == null)
						Output.initialError("Checkpoint completed from job started by foreign program (probably GUI).  Exiting...");
					args = l.evolutionState.runtimeArguments;                            // restore runtime arguments from checkpoint
					l.currentJob = ((Integer)(l.evolutionState.job[0])).intValue() + 1;  // extract next job number
				}
				catch (Exception e)
				{
					Output.initialError("EvolutionState's jobs variable is not set up properly.  Exiting...");
				}

				l.evolutionState.run(EvolutionState.C_STARTED_FROM_CHECKPOINT);
				cleanup(l.evolutionState);
			}

			/*  A this point we've finished out any previously-checkpointed job.  If there was
			 *  one such job, we've updated the current job number (currentJob) to the next number.
			 *  Otherwise currentJob is 0.
			 */

			/* Now we're going to load the parameter database to see if there are any more jobs.
			 * We could have done this using the previous parameter database, but it's no big deal.
			 */
			l.parameterDatabase = loadParameterDatabase(args);
			if (l.currentJob == 0)  // no current job number yet
				l.currentJob = l.parameterDatabase.getIntWithDefault(new Parameter("current-job"), null, 0);

			if (l.currentJob < 0)
				Output.initialError("The 'current-job' parameter must be >= 0 (or not exist, which defaults to 0)");

			int numJobs = l.parameterDatabase.getIntWithDefault(new Parameter("jobs"), null, 1);
			if (numJobs < 1)
				Output.initialError("The 'jobs' parameter must be >= 1 (or not exist, which defaults to 1)");

			numberOfJobs = numJobs;

			/* load the parameter database (reusing the very first if it exists) */
			if (l.parameterDatabase == null)
				l.parameterDatabase = loadParameterDatabase(args);

			// Initialize the EvolutionState, then set its job variables
			l.evolutionState = initialize(l.parameterDatabase, job);       // pass in job# as the seed increment
			l.evolutionState.output.systemMessage("Job: " + job);
			l.evolutionState.job = new Object[1];                          // make the job argument storage
			l.evolutionState.job[0] = Integer.valueOf(job);                // stick the current job in our job storage
			l.evolutionState.runtimeArguments = args;                      // stick the runtime arguments in our storage
			if (numJobs > 0)                                               // only if iterating (so we can be backwards-compatible),
			{
				//String jobFilePrefix = "job." + job + ".";
				String jobFilePrefix = "job." + job + ".alps."+l.getId() + ".";
				l.evolutionState.output.setFilePrefix(jobFilePrefix);      // add a prefix for checkpoint/output files 
				l.evolutionState.checkpointPrefix = jobFilePrefix + l.evolutionState.checkpointPrefix;  // also set up checkpoint prefix
			}

			/* Here you can set up the EvolutionState's parameters further before it's setup(...).
			 * This includes replacing the random number generators, changing values in state.parameters,
			 * changing instance variables (except for job and runtimeArguments, please), etc.
			 */
			l.result = EvolutionState.R_NOTDONE;

			/* set other parameters */
			l.setEvaluations(alpsEvaluations); //TODO not accurate
			l.evolutionState.generation = alpsEvaluations; //TODO not accurate
			l.evolutionState.startFresh(l);
		}
		
		/* determine number of evaluations */
		if(alpsLayers.get(0).evolutionState.numEvaluations==0) {
			alpsEvaluations = alpsLayers.get(0).evolutionState.numGenerations * AgingScheme.alpsAgeLayers * generationSize;
		} else {
			alpsEvaluations = (int) alpsLayers.get(0).evolutionState.numEvaluations;
		}

		
		
	}



	public void setup( final ParameterDatabase parameters)
	{

		//output was already created for us. 
		buildOutput().systemMessage(Version.message());
		/* setup parameter for ageScheme e.g linear, polynomial, exponential etc */
		ageScheme       = (AgingScheme)
				(parameters.getInstanceForParameter(base().push(AGING_SCHEME),null,AgingScheme.class));
		ageScheme.setup(parameters);

		// try
		// {   /* Get FSALPS frequency count strategy */
		// 	roulette = (Roulette)
		// 			(parameters.getInstanceForParameter(FSALPS.defaultBase().push(FSALPS_ROULETTE),null,Roulette.class));
		// 	fsalps_active = true;
		// }
		// catch(ec.util.ParamClassLoadException e)
		// {
		// 	fsalps_active = false; //when using normal ALPS, deactivate fsalps
		// } 


		numGenerations  = parameters.getInt(new Parameter(EvolutionState.P_GENERATIONS), null);

		if (!parameters.exists(base().push(AGE_ONLY_CURRENT_LAYER), null))
			System.out.println("default value for  "
					+ "\"alps."+AGE_ONLY_CURRENT_LAYER+ "\" of \""+alps_age_only_current_layer+"\" will be used \n");

		if (!parameters.exists(base().push(ALWAYS_BREED_MAXIMUM_POP), null))
			System.out.println("default value for  "
					+ "\"alps."+ALWAYS_BREED_MAXIMUM_POP+ "\" of \""+always_breed_maximum_pop+"\" will be used \n");

		alps_age_only_current_layer      =  
				parameters.getBoolean(base().push(AGE_ONLY_CURRENT_LAYER),null,false);
		always_breed_maximum_pop         =  
				parameters.getBoolean(base().push(ALWAYS_BREED_MAXIMUM_POP),null,true);	
		alps_assign_max_parent_age       =  
				parameters.getBoolean(base().push(ASSIGN_MAX_PARENT_AGE),null,true);
		
	
		// fsalps_use_only_default_node_pr  =  
		// 		parameters.getBoolean(base().push(FSALPS_USE_ONLY_DEFAULT_NODE_PR_PARAM),null,false);
		// fsalps_use_mutation              =  
		// 		parameters.getBoolean(base().push(FSALPS_USE_MUTATION_PARAM),null,true);
		// fsalps_gen_freq_count            =  
		// 		parameters.getBoolean(base().push(FSALPS_GEN_FREQ_COUNT),null,false);
		// fsalps_count_all_layers          =  
		// 		parameters.getBoolean(base().push(FSALPS_COUNT_ALL_LAYERS),null,false);
		
		/* p = new Parameter(K_FOLD_CROSS_VALIDATION_CHUNCK);
		 * number of chunks available when using k-fold cross validation 
		 * kFoldCrossValidationSize  = parameters.getInt(p, null, 1); */

	}


	/** 
	 * Initializes an evolutionary run given the parameters and a random seed adjustment (added to each random seed).
       The adjustment offers a convenient way to change the seeds of the random number generators each time you
       do a new evolutionary run.  You are of course welcome to replace the random number generators after initialize(...)
       but before startFresh(...) 

       <p>This method works by first setting up an Output (using buildOutput), then calling initialize(ParameterDatabase, seed, output)
	 */

	public static EvolutionState initialize(ParameterDatabase parameters, int randomSeedOffset)
	{
		return initialize(parameters, randomSeedOffset, buildOutput());
	}



	/** 
	 * Initializes an evolutionary run given the parameters and a random seed adjustment (added to each random seed),
     * with the Output pre-constructed.
     * The adjustment offers a convenient way to change the seeds of the random number generators each time you
     * do a new evolutionary run.  You are of course welcome to replace the random number generators after initialize(...)
     * but before startFresh(...) 
     */

	public static EvolutionState initialize(ParameterDatabase parameters, int randomSeedOffset, Output output)
	{
		EvolutionState state = null;
		MersenneTwisterFast[] random;
		int[] seeds;
		int breedthreads = 1;
		int evalthreads = 1;
		boolean store;
		int x;

		// Should we muzzle stdout and stderr?

		if (parameters.exists(new Parameter(P_MUZZLE), null))
			output.warning("" + new Parameter(P_MUZZLE) + " has been deprecated.  We suggest you use " + 
					new Parameter(P_SILENT) + " or similar newer options.");

		if (parameters.getBoolean(new Parameter(P_SILENT), null, false) ||
				parameters.getBoolean(new Parameter(P_MUZZLE), null, false))
		{
			output.getLog(0).silent = true;
			output.getLog(1).silent = true;
		}

		// output was already created for us. 
		/* COMMENTED OUT AND MOVED TO EVOLUTION STATE  */
		//output.systemMessage(Version.message());
		
		// 2. set up thread values
		breedthreads = Evolve.determineThreads(output, parameters, new Parameter(P_BREEDTHREADS));
		evalthreads  = Evolve.determineThreads(output, parameters, new Parameter(P_EVALTHREADS));
		boolean auto = (V_THREADS_AUTO.equalsIgnoreCase(parameters.getString(new Parameter(P_BREEDTHREADS),null)) ||
				V_THREADS_AUTO.equalsIgnoreCase(parameters.getString(new Parameter(P_EVALTHREADS),null)));  // at least one thread is automatic.  Seeds may need to be dynamic.

		// 3. create the Mersenne Twister random number generators,
		// one per thread
		random = new MersenneTwisterFast[breedthreads > evalthreads ? 
				breedthreads : evalthreads];
		seeds = new int[random.length];

		String seedMessage = "Seed: ";
		int time = (int)(System.currentTimeMillis());
		for (x=0;x<random.length;x++)
		{   /*@author: anthony modified to ensure different seed for all layers  */
			seeds[x] = determineSeed(output, parameters, new Parameter(P_SEED).push(""+x),
					time+x,random.length * randomSeedOffset, auto) + Engine.seed;
			for (int y=0;y<x;y++)
				if (seeds[x]==seeds[y])
					output.fatal(P_SEED+"."+x+" ("+seeds[x]+") and "+P_SEED+"."+y+" ("+seeds[y]+") ought not be the same seed.",null,null); 
			random[x] = Evolve.primeGenerator(new MersenneTwisterFast(seeds[x]));    // we prime the generator to be more sure of randomness.
			seedMessage = seedMessage + seeds[x] + " ";
		}

		// 4.  Start up the evolution
		// what evolution state to use?
		state = (EvolutionState)
				parameters.getInstanceForParameter(new Parameter("state"),null,
						EvolutionState.class);

		state.parameters = parameters;
		state.random = random;
		state.output = output;
		state.evalthreads = evalthreads;
		state.breedthreads = breedthreads;
		state.randomSeedOffset = randomSeedOffset;

		/*
		  output.systemMessage("-------------ALPS SYSTEM-------------");
		  output.systemMessage("Ageing Scheme "+ ageScheme.toString().substring(0, ageScheme.toString().length()-9));
		  output.systemMessage("Layers "+alpsAgeLayers );
		  output.systemMessage("Age Gap "+alpsAgeGap );
		 */
		output.systemMessage("Threads:  breed/" + breedthreads + " eval/" + evalthreads);
		output.systemMessage(seedMessage);

		return state;
	}



	/**
	 * now we let it go
	 * clears up the system after every run
	 * @param alps
	 */
	public static void clearSystem(ALPSLayers alps)
	{
		for(Layer l: alps.layers)
		{
			cleanup(l.evolutionState);  // flush and close various streams, print out parameters if necessary
			l.parameterDatabase = null;  // so we load a fresh database next time around
			l.evolutionState.finish(l.result);
		}
		completeGenerationalCount  = 0;
		completeEvaluationCount    = 0;
		globalEvaluations          = 0;
	}
	


	/** Top-level evolutionary loop.  */

	public static void main(String[] args)
	{

		ParameterDatabase pd      =  loadParameterDatabase(args) ;

		/* Now we know how many jobs remain.  Let's loop for that many jobs.  Each time we'll
		 * load the parameter database scratch (except the first time where we reuse the one we
		 * just loaded a second ago).  The reason we reload from scratch each time is that the
		 * experimenter is free to scribble all over the parameter database and it'd be nice to
		 * have everything fresh and clean.  It doesn't take long to load the database anyway,
		 * it's usually small.
		 */
		//for(int job = currentJob ; job < numJobs; job++)
		//TODO checkpointing
		for(int job = 0 ; job < numberOfJobs; job++)
		{
			/* We used to have a try/catch here to catch errors thrown by this job and continue to the next.
			 * But the most common error is an OutOfMemoryException, and printing its stack trace would
			 * just create another OutOfMemoryException!  Which dies anyway and has a worthless stack
			 * trace as a result.
			 */

			new Engine(args,job,pd);

			/* all running instances of Evolution state have access to ALPS Layers */
			alps = new ALPSLayers(alpsLayers,0);

			/* this must be called after new Engine(args,job,pd) to setup the parameters */
			//alpsEvaluations = numGenerations * AgingScheme.alpsAgeLayers * (Engine.generationSize + 1);


			/* 
			 * Sequential selection
			 * the big loop 
			 * wrap up when the last layer completes evaluation
			 * used to be this: "Engine.globalEvaluations <= alpsEvaluations",  "Engine.completeGenerationalCount <= alpsEvaluations"
			 */
			while(alps.layers.get(alps.layers.size()-1).result == EvolutionState.R_NOTDONE) 
			{
			
				for(int j=alpsLayers.size()-1;j>=0;j--)
				{  
					
					alps.index =  j; //only modify the index
					if(alpsLayers.get(j).getIsBottomLayer() ) //set initializer flag to true when bottom layer is called
					{ 
						
						if( (alpsLayers.get(j).layerGenerationalCount == 1) 
								|| (alpsLayers.get(j).evolutionState.population.subpops.get(0).individuals.size()>0 ) 
								|| alpsLayers.get(j).initializerFlag  )
						{	
							alpsLayers.get(j).evolutionState.run(alps,EvolutionState.C_STARTED_FRESH); //good
							alpsLayers.get(j).initializerFlag      = false; 
						}

						// -1 because the age of the individuals increases before the completeGenerationalCount increases
						if(completeGenerationalCount >= alpsLayers.get(j).getMaxAge() && (completeGenerationalCount % alpsLayers.get(j).getMaxAge())==0) 
						{   
							alpsLayers.get(j).initializerFlag      = true; 
							alpsLayers.get(j).layerGenerationalCount = 1;
							//alpsLayers.get(j).evolutionState.generation = 0; //problems with counting when enabled
						}
					} 
					else if((alpsLayers.get(j).evolutionState.population.subpops.get(0).individuals.size()>0) && //remove if problematic
							completeGenerationalCount > alpsLayers.get(j-1).getMaxAge() )
					{ 
						
						/*Generational worked without this condition */
						alpsLayers.get(j).evolutionState.run(alps,EvolutionState.C_STARTED_FRESH); //good was put here because of SteadyState -- remove if problematic
						

						if((alpsLayers.get(j).layerGenerationalCount % alpsLayers.get(j).getGenerations())==0)
						{
							alpsLayers.get(j).layerGenerationalCount      = 1;
							//alpsLayers.get(j).evolutionState.generation = 0; //problems with counting when enabled
						}
					}

				} 
				completeGenerationalCount++; //commented to solve problem with stats for layer 0
				//all layers have the same default population size
				completeEvaluationCount += generationSize; 
			} //end big loop
			clearSystem(alps); // now we let it go
		}//end jobs
		System.exit(0);
	}



}
