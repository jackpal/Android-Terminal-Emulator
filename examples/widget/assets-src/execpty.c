#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/select.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <errno.h>

#define BUFSIZE 4096
char buf[BUFSIZE];

pid_t pid;

static void do_copy(int dst, int src);
static int do_exec(char **argv, char **envp);

/**
 * Example of how to obtain a pty and connect a process to it using C and the
 * POSIX API.
 *
 * Traditionally, a tty device is an actual piece of hardware such as a serial
 * port.  You would connect a physical VT100 terminal (other models of terminal
 * are available) to the serial port, and your program would interact with the
 * terminal by reading and writing to the tty device.
 *
 * When emulating a terminal in software, we ask the kernel to create a
 * "pseudoterminal" (pty) device for us.  How exactly this is done has varied
 * over the decades, but these days, we open /dev/ptmx (the "master" device),
 * which causes the kernel to create a new ("slave") pty under /dev/pts.  The
 * program running in the terminal will do I/O to the slave pty, but instead of
 * sending the data to hardware, the kernel will pass it to us over the file
 * descriptor we got when we opened the master device.  See do_exec() for
 * details of how to do this.
 *
 * In addition, this program passes data between TermSession in the Java
 * process and the tty via its standard input/output.  You may choose instead
 * to place the code to obtain the pty and create the process in a native
 * method (JNI library) and invoke it from Java.  In that case, you will
 * instead need to make a java.io.FileDescriptor object holding the master
 * device's file descriptor in the VM and return that object from your native
 * method.
 */
int main(int argc, char **argv, char **envp) {
	int stdin_fd = 0, stdout_fd = 1, ptmx_fd;
	fd_set readfds;

	if (argc < 2) {
		fprintf(stderr, "execpty: no arguments given\r\n");
		return 1;
	}

	/* Create the pty and run the target program using it */
	if ((ptmx_fd = do_exec(argv+1, envp)) == -1) {
		return 1;
	}

	/**
	 * Loop waiting for either the master device or standard input to
	 * become readable, and copy data (from the master device to standard
	 * output, or from standard input to the master device) as needed.
	 *
	 * See the documentation for select(2) for details.
	 */
	FD_SET(stdin_fd, &readfds);
	FD_SET(ptmx_fd, &readfds);

	while (select(ptmx_fd+1, &readfds, NULL, NULL, NULL) >= 0) {
		if (FD_ISSET(stdin_fd, &readfds)) {
			do_copy(ptmx_fd, stdin_fd);
		}
		if (FD_ISSET(ptmx_fd, &readfds)) {
			do_copy(stdout_fd, ptmx_fd);
		}

		FD_ZERO(&readfds);
		FD_SET(stdin_fd, &readfds);
		FD_SET(ptmx_fd, &readfds);
	}

	/* We only get here if select() errors out */
	perror("execpty: select() failed:");
	return 1;
}

/**
 * Open the master device, obtain a slave pty, create a new process, connect
 * it to the slave pty, and exec the program specified by argv[0] with
 * arguments argv[][] and environment envp[][].  Returns the file descriptor
 * number of the master device.
 */
static int do_exec(char **argv, char **envp) {
	int ptmx_fd;
	char *slave_pty;

	/* Open the master device. */
	ptmx_fd = open("/dev/ptmx", O_RDWR);

	/**
	 * Obtain permissions on the slave pty and unlock it for use.  See
	 * grantpt(2) and unlockpt(2) documentation for details.
	 *
	 * PS: On a Linux system with Unix98 ptys (/dev/pts) such as any
	 * Android device, grantpt() is actually a no-op, but POSIX says we
	 * should make these system calls in this order.
	 *
	 * PPS: Since grantpt() is a no-op, the requirement to unlock the slave
	 * pty is the only thing stopping us from doing this entirely in Java.
	 * (A tiny Linux kernel patch would lift that requirement, but would be
	 * highly unlikely to ever be accepted.)
	 */
	if (grantpt(ptmx_fd) || unlockpt(ptmx_fd)) {
		return -1;
	}

	/* Get the path to the slave pty device.  See ptsname(2) documentation
	   for details. */
	slave_pty = ptsname(ptmx_fd);

	/* Create a child process (which will initially be a clone of the
	   parent process). See fork(2) documentation for details. */
	if ((pid = fork()) == -1) {
		perror("execpty: fork() failed:");
		return -1;
	}

	if (pid == 0) {
		/* fork() returning 0 says that we are the child process. */

		int fd;

		/* Close the master device fd, as we won't need it. */
		close(ptmx_fd);

		/**
		 * Become process group leader and session leader.  Exactly
		 * what this does is somewhat arcane, but the result is
		 * important for job control in the shell.  Many shells will
		 * do this if we don't take care of it for them.
		 *
		 * See setsid(2) documentation and the definitions of
		 * "session", "session leader", "process group", "process group
		 * leader" and "controlling terminal" in the POSIX standard if
		 * you really want to understand what this does.
		 */
		setsid();

		/**
		 * Open the slave pty and use the resulting file descriptor as
		 * standard input, output, and error.
		 *
		 * Because the slave pty is a terminal device, and one of
		 * setsid()'s effects is to detach the calling process from any
		 * controlling terminal, we will pick up the slave pty as our
		 * controlling terminal when we open it.  See the documentation
		 * for open(2), particularly the flag O_NOCTTY, for details.
		 *
		 * Normally, we'd open two descriptors, one for reading only
		 * for standard input, and one for writing only for standard
		 * output/error.  But the Android shell tries to write its
		 * prompt to standard input (probably a bug), so we need
		 * stdin to be open for both reading and writing.
		 */
		if ((fd = open(slave_pty, O_RDWR)) == -1) {
			exit(1);
		}
		dup2(fd, 0);
		dup2(fd, 1);
		dup2(fd, 2);
		close(fd);

		/**
		 * Try to start the target program with the provided arguments
		 * and environment variables, and exit (the child process) if
		 * it fails.
		 */
		if (execve(argv[0], argv, envp)) {
			exit(1);
		}
	}

	/* Only the parent process will run this code. */
	return ptmx_fd;
}

/**
 * Copy as much data as we can read from the src fd without blocking (up to
 * BUFSIZE bytes) to the dst fd.
 */
static void do_copy(int dst, int src) {
	int count, saved_errno = 0, status;

	memset(buf, 0, BUFSIZE);

	count = read(src, buf, BUFSIZE);
	switch (count) {
	case -1:
		saved_errno = errno;
		if (saved_errno != EIO) {
			fprintf(stderr, "execpty: read(%d, ...) failed: %s\r\n",
					src, strerror(saved_errno));
			exit(1);
		}
		/* EIO: process exited? fall through */
	case 0:
		if (waitpid(pid, &status, WNOHANG) == 0) {
			/* process didn't exit? */
			if (saved_errno > 0) {
				fprintf(stderr,
					"execpty: read(%d, ...) failed: %s\r\n",
					src, strerror(saved_errno));
				exit(1);
			} else {
				break;
			}
		}

		/* EOF (process exited) */
		if (WIFEXITED(status)) {
			printf("Process exited with status %d\r\n",
					WEXITSTATUS(status));
			exit(0);
		} else if (WIFSIGNALED(status)) {
			printf("Process killed by signal %d\r\n",
					WTERMSIG(status));
			exit(0);
		}
		break;
	default:
		write(dst, buf, count);
		break;
	}
}
