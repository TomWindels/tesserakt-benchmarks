const ReplayBenchmarkReplayer = require('@tesseraktjs/testing-tooling-replay-benchmark').ReplayBenchmarkReplayer;
const RDFJS = require('@tesseraktjs/testing-tooling-replay-benchmark').RDFJS;
const fs = require('fs');
const incremunica = require('@incremunica/query-sparql-incremental');
const { workerData, isMainThread, Worker } = require('worker_threads');
const StreamingStore = require('@incremunica/streaming-store').StreamingStore;
const isAddition = require('@incremunica/user-tools').isAddition;

function get_checksum_for_term(term) {
    if (term.termType == 'NamedNode') {
        return term.value.length;
    }
    if (term.termType === 'Literal') {
        return term.value.length;
    }
    if (term.termType === 'BlankNode') {
        return 1;
    }
    throw new Error(`Unsupported term type: ${term.termType}`);
}

function get_checksum_for_bindings(binding) {
    let checksum = 0;
    for (const [_, term] of binding) {
        checksum += get_checksum_for_term(term);
    }
    return checksum;
}

function get_checksum_for_resultset(results) {
    let checksum = 0;
    results.forEach((count, binding) => {
        checksum += count * get_checksum_for_bindings(binding);
    });
    return checksum;
}

async function dynamic_timeout(current_delay) {
    let delay = current_delay();
    while (delay > 0) {
        await new Promise((resolve) => setTimeout(resolve, delay));
        delay = current_delay();
    }
}

async function observe_results(total, bindingsStream) {
    const start = process.hrtime.bigint();
    let latest = start;
    // observing the changes
    let insertions = 0;
    let deletions = 0;
    new Promise(() => {
        bindingsStream.on('data', (binding) => {
            latest = process.hrtime.bigint();
            if (isAddition(binding)) {
                insertions++;
                total.set(binding, (total.get(binding) || 0) + 1)
            } else {
                deletions++;
                total.set(binding, (total.get(binding) || 0) - 1)
            }
        });
    });
    // wait for 2 seconds of inactivity
    await dynamic_timeout(() => Number(latest - process.hrtime.bigint()) / 1_000_000 + 2000);
    // cleaning up
    bindingsStream.removeAllListeners('data');
    const duration = Number(latest - start) / 1_000_000;
    const checksum = get_checksum_for_resultset(total);
    console.log(`|`, insertions, `added,`, deletions, `deleted\n| Checksum value is`, checksum, `\n> Processing took`, duration, `ms`);
    // TODO: return the duration, insertions, deletions, and the checksum
}

async function eval_query(query, replayer) {
    const store = new StreamingStore();
    const engine = new incremunica.QueryEngine();
    const stream = await engine.queryBindings(query, { sources: [store] });
    // collecting all results ever emitted here
    const total = new Map();
    // shouldn't do anything given that the store is empty, but let's be sure
    // TODO: collect the results
    console.log('* initial state')
    await observe_results(total, stream);

    const diffs = [];
    replayer.forEachSnapshot((_, diff) => { diffs.push(diff); });
    for (const diff of diffs) {
        let insertions = RDFJS.fromSet(diff.insertions);
        let deletions = RDFJS.fromSet(diff.deletions);
        console.log(`\nΔ`, insertions.size, `insertions,`, deletions.size, `deletions`);
        insertions.forEach((quad) => {
            store.addQuad(quad);
        });
        deletions.forEach((quad) => {
            store.removeQuad(quad);
        });
        // TODO: collect the results
        await observe_results(total, stream);
    }
    // TODO: return the collected results
}

async function eval_replayer(filename, replayer) {
    for (const query of replayer.queries) {
        console.log(`Evaluating query: ${query}`);
        // TODO store the results
        await eval_query(query, replayer);
    }
    // TODO write the results to an appropriate location based on filename & cwd
}

async function eval(files) {
    for (const file of files) {
        // starting a worker per file
        const worker = new Worker(__filename, {workerData: file});
        console.log(`Replaying file: ${file}`);
        worker.on('message', (message) => {
            console.log(`Got ${message}`);
        });
        await new Promise((resolve) => {
            worker.on('error', (a) => {
                console.log(`Received`, a, `in \`error\` request!`);
                resolve()
            });
            worker.on('exit', (a) => {
                console.log(`Received`, a, `in \`exit\` request!`);
                resolve()
            });
        });
    }
}

if (isMainThread) {
    const directory = process.argv[2];
    if (!directory) {
        console.error('Please provide a directory path as an argument.');
        process.exit(1);
    }
    const files = fs.readdirSync(directory).map((file) => `${directory}/${file}`);

    eval(files).catch((error) => {
        console.error('Error during evaluation:', error);
    });
} else {
    if (workerData != undefined) {
        const filename = workerData;
        console.log(`Worker started for file ${filename}`)
        const replay = ReplayBenchmarkReplayer.fromFile(filename);
        eval_replayer(filename, replay);
    } else {
        console.error("No worker data available!");
        exit(1);
    }
}
