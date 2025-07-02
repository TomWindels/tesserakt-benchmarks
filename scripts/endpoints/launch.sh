#! /bin/bash

if ! [[ "$1" =~ ^[0-9]{1,4}$ ]] ; then
	echo "The first argument is not a number (endpoint expected, 1 to 4 numbers, got $1)" >&2; exit 1
fi

ENDPOINT_PORT=$1

await_endpoint() {
	echo "Waiting until the port ${ENDPOINT_PORT} becomes available"
	sleep 2
	{
	  while ! echo -n > /dev/tcp/localhost/${ENDPOINT_PORT}; do
	    echo -n "."
	    sleep 2
	  done
	} 2>/dev/null
	
	printf "\nEndpoint is ready!\n"
}

shift 1

MANAGED_HERE=false
if [[ "$1" == "--" ]]; then
	MANAGED_HERE=true
fi

exec_exit() {
	if [[ "${MANAGED_HERE}" == true ]]; then
		echo "Shutting down..."
		kill $!
	fi
	exit
}

await_exit() {
	echo -n "Press enter to stop the server again, or use ^C / SIGINT, e.g. \`kill -SIGINT $(echo $$)\`"
	trap "echo;exec_exit" SIGINT
	read
}

if [[ "${MANAGED_HERE}" == 0 ]]; then
	echo "No launch commands provided; assuming the server is already launched..."
	await_endpoint
	await_exit
else
	shift 1
	echo "Starting the server using the following command:"
	echo "$@"
	"$@" &
	await_endpoint
	await_exit
	exec_exit
fi


