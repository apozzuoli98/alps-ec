package ec.alps.gp.breed;

import java.util.ArrayList;
import java.util.HashMap;

import ec.EvolutionState;
import ec.Individual;
import ec.alps.Engine;
import ec.EvolutionState;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;
import ec.util.IntBag;

/**
 * ALPS: AGE INCREMENT
 * 
 * Each generation in which an individual is used as a parent to create an offspring, its
 * age is increased by 1 since its genetic material has been used in evolution in another generation
 * ---Greg Hornby
 * 
 * Increase age of individuals used as parents :::
 * 
 * For example, the GP CrossoverPipeline asks for one Individual of each of its two children, 
 * which must be genetic programming Individuals, performs subtree crossover on those Individuals, 
 * then hands them to its parent. --  Sean Luke - ECJ Manual
 * 
 * @author Anthony Awuley
 */
public class Crossover extends CrossoverPipeline{

	/** */
	private static final long serialVersionUID = 1;

	/** Temporary holding place for alps parent selection */
	private GPIndividual alpsParents[];

	private final String SELECTION_PRESSURE = "selection-pressure";


	public int produce(final int min, 
			final int max, 
			final int subpopulation,
			final ArrayList<Individual> inds,
			final EvolutionState state,
			final int thread,
			final HashMap<String,Object> misc) 

	{ 

		int start = inds.size(); // ECJ27



		double selectionPressure = state.parameters.getDouble(Engine.base().push(SELECTION_PRESSURE), null);


		// how many individuals should we make?
		int n = typicalIndsProduced();
		if (n < min) n = min;
		if (n > max) n = max;


		/** ECJ 27 */
		IntBag[] parentparents = null;
		IntBag[] preserveParents = null;


        if (misc!=null && misc.containsKey(KEY_PARENTS))
            {
            preserveParents = (IntBag[])misc.get(KEY_PARENTS);
            parentparents = new IntBag[2];
            misc.put(KEY_PARENTS, parentparents);
            }
        
        // should we use them straight?
        if (!state.random[thread].nextBoolean(likelihood))
            {
            // just load from source 0 and clone 'em
            sources[0].produce(n,n,subpopulation,inds, state,thread,misc);
            return n;
            }


		// // should we bother?
		// if (!state.random[thread].nextBoolean(likelihood))
		// 	return reproduce(n, start, subpopulation, inds, state, thread, true);  // DO produce children from source -- we've not done so already


		GPInitializer initializer = ((GPInitializer)state.initializer);

		for(int q=start;q<n+start; /* no increment */)  // keep on going until we're filled up
		{
			// grab two individuals from our sources
			if (sources[0]==sources[1])  // grab from the same source
			{
				if(Engine.alps.layers.get(Engine.alps.index).getIsBottomLayer())
				{
					sources[0].produce(2,2,subpopulation,parents,state,thread,misc);
				}
				else
				{ 
					for(int u=0;u<=1;u++)
					{
						/**
						 * stores parents of first selection on second loop. 
						 * IMPortant to clone else, will end up with reference of second parents selection in alpsParents
						 * which results in selection from the same source
						 */
						alpsParents = (GPIndividual[]) parents.clone(); 

						//perform selection from current population if previous population is empty
						if((state.random[0].nextDouble()<=selectionPressure) || 
								Engine.alps.layers.get(Engine.alps.index-1).evolutionState.population.subpops.get(subpopulation).individuals.size()==0)
						{
							sources[0].produce(2,2,subpopulation,parents,state,thread,misc);
						}
						else //selecting from lower layer
						{
							sources[0].produce(2,2,subpopulation,parents,
									Engine.alps.layers.get(Engine.alps.index-1).evolutionState,thread,misc);
							/* When this flag is enabled, prevent increasing age for idividuals selected from lower layer for breeding  */
							if(Engine.alps_age_only_current_layer)
								for(Individual id : parents)
									id.generationCount       = state.generation;
						}
					}

					/** 
					 * the second instance of the loop has new individuals
					 * maintain second individual and replace first with previously selected 
					 * first parent parents.get(0) = alpsparents.get(0);
					 * */
					parents.set(1, alpsParents[0]);

				}
			}
			else // grab from different sources
			{
				if(Engine.alps.layers.get(Engine.alps.index).getIsBottomLayer())
				{
					sources[0].produce(1,1,subpopulation,parents,state,thread,misc);
					sources[1].produce(1,1,subpopulation,parents,state,thread,misc);
				}
				else
				{
					for(int u=0;u<=1;u++)
					{
						alpsParents = (GPIndividual[]) parents.clone(); //stores parents of first selection on second loop

						if((state.random[0].nextDouble()<=selectionPressure) || 
								Engine.alps.layers.get(Engine.alps.index-1).evolutionState.population.subpops.get(subpopulation).individuals.size()<=0)
						{
							sources[0].produce(1,1,subpopulation,parents,state,thread,misc);
							sources[1].produce(1,1,subpopulation,parents,state,thread,misc);
						}
						else //selecting from lower layer
						{
							sources[0].produce(1,1,subpopulation,parents,
									Engine.alps.layers.get(Engine.alps.index-1).evolutionState,thread,misc);
							sources[1].produce(1,1,subpopulation,parents,
									Engine.alps.layers.get(Engine.alps.index-1).evolutionState,thread,misc);
							/* When this flag is enabled, prevent increasing age for idividuals selected from lower layer for breeding  */
							if(Engine.alps_age_only_current_layer)
								for(Individual id : parents)
									id.generationCount       = state.generation; 
						}
					}
				}

				/** 
				 * the second instance of the loop has new individuals
				 * maintain second individual and replace first with previously selected 
				 * first parent parents.get(1) = alpsparents.get(0);
				 * */
				parents.set(1, alpsParents[0]);


			}

			//System.out.println("System out println : "+parents.length); System.exit(0);

			
			for(int id=0;id<parents.size();id++)
			{
				if(state.generation != parents.get(id).generationCount/*!parents[k].parentFlag*/) 
				{
					parents.get(id).age++;
					parents.get(id).generationCount = state.generation;
				}
			}


			// at this point, parents[] contains our two selected individuals

			// are our tree values valid?
			if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= ((GPIndividual)parents.get(0)).trees.length))
				// uh oh
				state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual"); 
			if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= ((GPIndividual)parents.get(0)).trees.length))
				// uh oh
				state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual"); 

			int t1=0; int t2=0;
			if (tree1==TREE_UNFIXED || tree2==TREE_UNFIXED) 
			{
				do
					// pick random trees  -- their GPTreeConstraints must be the same
				{
					if (tree1==TREE_UNFIXED) 
						if (((GPIndividual)parents.get(0)).trees.length > 1)
							t1 = state.random[thread].nextInt(((GPIndividual)parents.get(0)).trees.length);
						else t1 = 0;
					else t1 = tree1;

					if (tree2==TREE_UNFIXED) 
						if (((GPIndividual)parents.get(0)).trees.length>1)
							t2 = state.random[thread].nextInt(((GPIndividual)parents.get(0)).trees.length);
						else t2 = 0;
					else t2 = tree2;
				} while (((GPIndividual)parents.get(0)).trees[t1].constraints(initializer) != ((GPIndividual)parents.get(0)).trees[t2].constraints(initializer));
			}
			else
			{
				t1 = tree1;
				t2 = tree2;
				// make sure the constraints are okay
				if (((GPIndividual)parents.get(0)).trees[t1].constraints(initializer) 
						!= ((GPIndividual)parents.get(0)).trees[t2].constraints(initializer)) // uh oh
					state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
			}



			// validity results...
			boolean res1 = false;
			boolean res2 = false;


			// prepare the nodeselectors
			nodeselect1.reset();
			nodeselect2.reset();


			// pick some nodes

			GPNode p1=null;
			GPNode p2=null;

			for(int x=0;x<numTries;x++)
			{
				// pick a node in individual 1
				p1 = nodeselect1.pickNode(state,subpopulation,thread,((GPIndividual)parents.get(0)),((GPIndividual)parents.get(0)).trees[t1]);

				// pick a node in individual 2
				p2 = nodeselect2.pickNode(state,subpopulation,thread,((GPIndividual)parents.get(0)),((GPIndividual)parents.get(0)).trees[t2]);

				// check for depth and swap-compatibility limits
				res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
				if (n-(q-start)<2 || tossSecondParent) res2 = true;
				else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

				// did we get something that had both nodes verified?
				// we reject if EITHER of them is invalid.  This is what lil-gp does.
				// Koza only has numTries set to 1, so it's compatible as well.
				if (res1 && res2) break;
			}

			// at this point, res1 AND res2 are valid, OR either res1
			// OR res2 is valid and we ran out of tries, OR neither is
			// valid and we ran out of tries.  So now we will transfer
			// to a tree which has res1 or res2 valid, otherwise it'll
			// just get replicated.  This is compatible with both Koza
			// and lil-gp.


			// at this point I could check to see if my sources were breeding
			// pipelines -- but I'm too lazy to write that code (it's a little
			// complicated) to just swap one individual over or both over,
			// -- it might still entail some copying.  Perhaps in the future.
			// It would make things faster perhaps, not requiring all that
			// cloning.



			// Create some new individuals based on the old ones -- since
			// GPTree doesn't deep-clone, this should be just fine.  Perhaps we
			// should change this to proto off of the main species prototype, but
			// we have to then copy so much stuff over; it's not worth it.

			GPIndividual j1 = (GPIndividual)(((GPIndividual)parents.get(0)).lightClone());
			GPIndividual j2 = null;
			if (n-(q-start)>=2 && !tossSecondParent) j2 = (GPIndividual)(((GPIndividual)parents.get(0)).lightClone());

			// Fill in various tree information that didn't get filled in there
			j1.trees = new GPTree[((GPIndividual)parents.get(0)).trees.length];
			if (n-(q-start)>=2 && !tossSecondParent) j2.trees = new GPTree[((GPIndividual)parents.get(0)).trees.length];

			// at this point, p1 or p2, or both, may be null.
			// If not, swap one in.  Else just copy the parent.

			for(int x=0;x<j1.trees.length;x++)
			{
				if (x==t1 && res1)  // we've got a tree with a kicking cross position!
				{ 
					j1.trees[x] = (GPTree)(((GPIndividual)parents.get(0)).trees[x].lightClone());
					j1.trees[x].owner = j1;
					j1.trees[x].child = ((GPIndividual)parents.get(0)).trees[x].child.cloneReplacing(p2,p1); 
					j1.trees[x].child.parent = j1.trees[x];
					j1.trees[x].child.argposition = 0;
					j1.evaluated = false; 
				}  // it's changed
				else 
				{
					j1.trees[x] = (GPTree)(((GPIndividual)parents.get(0)).trees[x].lightClone());
					j1.trees[x].owner = j1;
					j1.trees[x].child = (GPNode)(((GPIndividual)parents.get(0)).trees[x].child.clone());
					j1.trees[x].child.parent = j1.trees[x];
					j1.trees[x].child.argposition = 0;
				}
			}

			if (n-(q-start)>=2 && !tossSecondParent) 
				for(int x=0;x<j2.trees.length;x++)
				{
					if (x==t2 && res2)  // we've got a tree with a kicking cross position!
					{ 
						j2.trees[x] = (GPTree)(((GPIndividual)parents.get(0)).trees[x].lightClone());           
						j2.trees[x].owner = j2;
						j2.trees[x].child = ((GPIndividual)parents.get(0)).trees[x].child.cloneReplacing(p1,p2); 
						j2.trees[x].child.parent = j2.trees[x];
						j2.trees[x].child.argposition = 0;
						j2.evaluated = false; 
					} // it's changed
					else 
					{
						j2.trees[x] = (GPTree)(((GPIndividual)parents.get(0)).trees[x].lightClone());           
						j2.trees[x].owner = j2;
						j2.trees[x].child = (GPNode)(((GPIndividual)parents.get(0)).trees[x].child.clone());
						j2.trees[x].child.parent = j2.trees[x];
						j2.trees[x].child.argposition = 0;
					}
				}

			// add the individuals to the population
			inds.set(q, j1);
			/*
			 * ALPS: AGE INCREMENT
			 * increase age of offsping age by oldest parent
			 * 
			 * Individuals that are created through variation, such as by mutation or recombination, 
			 * take the age value of their oldest parent plus 1 since their genetic material comes from 
			 * their parents and has now been in the population for one more generation than their parents.
			 * --Greg Hornby
			 * 
			 * Since parents age has already been incremented, set offspring age to oldest parent
			 * 
			 * @author anthony
			 */
			
			/* offspring gets age of oldest/youngest parent */
			inds.get(q).age        = (Engine.alps_assign_max_parent_age)?
				                       Math.max(parents.get(0).age, parents.get(1).age):Math.min(parents.get(0).age, parents.get(1).age);
			/* get minimum/maximum evaluation for parent. the lowest evaluation count is the oldest parent */
			inds.get(q).evaluation = (Engine.alps_assign_max_parent_age)? 
					                  Math.min(parents.get(0).evaluation, parents.get(1).evaluation):Math.max(parents.get(0).evaluation, parents.get(1).evaluation);
			q++;
			if (q<n+start && !tossSecondParent)
			{
				inds.set(q, j2);
				/*
				 * ALPS: AGE INCREMENT
				 * increase age of offsping by oldest parent
				 * @author anthony
				 */
				/* offspring gets age of oldest/youngest parent */
				inds.get(q).age        = (Engine.alps_assign_max_parent_age)?
					                       Math.max(parents.get(0).age, parents.get(1).age):Math.min(parents.get(0).age, parents.get(1).age);
				/* get minimum/maximum evaluation for parent. the lowest evaluation count is the oldest parent */
				inds.get(q).evaluation = (Engine.alps_assign_max_parent_age)? 
						                  Math.min(parents.get(0).evaluation, parents.get(1).evaluation):Math.max(parents.get(0).evaluation, parents.get(1).evaluation);
				q++;
			}

		}
		return n;
	}



}
