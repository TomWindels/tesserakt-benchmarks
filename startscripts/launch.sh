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

await_exit() {
	echo -n "Press enter to stop the server again"
	read
}

shift 1

if [[ "$1" != "--" ]]; then
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
	echo "Shutting down..."
	kill $!
fi


