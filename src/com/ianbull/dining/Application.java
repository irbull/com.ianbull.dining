package com.ianbull.dining;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.service.datalocation.Location;

/**
 * A simple OSGi application that implements a solution to the Dining Philosophers problem
 * using the OSGi location service.
 * 
 * @author Ian Bull
 */
public class Application implements IApplication {

	public final static int TOTAL_PHILOSPHERS = 15;     // Total number of philosophers
	public final static int TOTAL_FOOD_TO_EAT = 50;     // Amount of food each philosopher needs to eat
	public final static int EAT_TIME = 200;             // A multiplier for eating food

	private Philosopher[] philosophers = new Philosopher[TOTAL_PHILOSPHERS];
	
	public class Philosopher extends Job {

		private final int number;
		int foodLeft = 0;
		private final Location leftForkLocation;
		private final Location rightForkLocation;

		public Philosopher(String name, int number, int foodRemaining, 
				Location leftForkLocation, Location rightForkLocation) {
			super(name);
			this.number = number;
			this.foodLeft = foodRemaining;
			this.leftForkLocation = leftForkLocation;
			this.rightForkLocation = rightForkLocation;
		}

		public boolean belongsTo(Object family) {
			if (family == Application.this)
				return true;
			return super.belongsTo(family);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				thinking: while (foodLeft > 0) {
					// Wait and do some thinking
					Thread.sleep(getThinkingTime());
					
					while (true) {
						// Continue until you can acquire both forks.  We could 
						// sleep for a bit if we don't acquire both forks, but
						// we are pretty hungry here, and sleeping is never good
						// on an empty stomach 
						boolean leftFork = false;
						boolean rightFork = false;
						try {
							leftFork = acquire(leftForkLocation);
							rightFork = rightForkLocation.lock();
							if (leftFork && rightFork) {
								eat();
								continue thinking;
							} 
						} catch (IOException e) {
							// do nothing, we will release everything below
						} finally {
							if (leftFork)
								release(leftForkLocation);
							if (rightFork)
								release(rightForkLocation);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return Status.OK_STATUS;
		}

		/**
		 * Blocks until a particular location can be locked
		 */
		private boolean acquire(Location l) throws IOException,
				InterruptedException {
			boolean lock = l.lock();
			if (lock)
				return true;
			while (true) {
				Thread.sleep(10);
				lock = l.lock();
				if (lock)
					return true;
			}
		}

		/**
		 * Releases the lock on a particular location
		 */
		private void release(Location l) {
			try {
				l.release();
			} catch (Exception e) {
				// Do nothing
			}
		}

		/**
		 * Eats a random amount of food, and prints the status of all 
		 * philosophers.  Eating takes 200ms for each unit of food.
		 */
		private void eat() throws InterruptedException {
			int food = getRandomAmountOfFood();
			foodLeft -= food;
			if (foodLeft < 0 ) 
				foodLeft = 0;
			Thread.sleep(food * 100);
			print();
		}
	}

	/**
	 * Utility method to print the remaining units of food for all philosophers
	 */
	private synchronized void print() {
		for (Philosopher philosopher : philosophers) {
			System.out.print(philosopher.number + " [" + philosopher.foodLeft + "] ");
		}
		System.out.println();
	}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		URI baseLocation = tempDir.toURI();
		for (int i = 0; i < TOTAL_PHILOSPHERS; i++) {
			philosophers[i] = new Philosopher("Philospher " + i, i, 50, getLockLocation(baseLocation, i), getLockLocation(baseLocation, (i+1) % TOTAL_PHILOSPHERS));
			philosophers[i].schedule();
		}
		Job.getJobManager().join(this, new NullProgressMonitor());

		System.out.println("All philosophers are done eating");
		return IApplication.EXIT_OK;
	}

	/**
	 * Returns how long the philosopher should 'think' for
	 */
	private static int getThinkingTime() {
		return (int) (Math.random() * 300);
	}

	/**
	 * Returns a random amount of food to consume
	 */
	private static int getRandomAmountOfFood() {
		return (int) (Math.random() * 10);
	}

	/**
	 * Get the lock location for a particular fork.
	 */
	private static Location getLockLocation(URI baseLocation, int locationNumber)
			throws IllegalStateException, IOException {
		Location anyLoc = (Location)  Activator.getService(Location.class.getName());
		URI locationURI = URIUtil.append(baseLocation, "" + locationNumber);
		Location location = anyLoc.createLocation(null, URIUtil.toURL(locationURI), false); //$NON-NLS-1$
		location.set(URIUtil.toURL(locationURI),false);
		return location;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}

}
