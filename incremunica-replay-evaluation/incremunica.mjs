import { createRequire } from 'module';
const require = createRequire(import.meta.url);

function local_require(name) {
    return require(`${import.meta.dirname}/node_modules/${name}`);
}

const incremunica = local_require('@incremunica/query-sparql-incremental');
const StreamingStore = local_require('@incremunica/streaming-store').StreamingStore;
const { DataFactory } = local_require('n3');
const isAddition = local_require('@incremunica/user-tools').isAddition;

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

async function dynamic_timeout(current_delay) {
    let delay = current_delay();
    while (delay > 0) {
        await new Promise((resolve) => setTimeout(resolve, delay));
        delay = current_delay();
    }
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

export class IncremunicaEvaluator {

    constructor(stream, store) {
        this.store = store;
        this.total = new Map();
        this.lastDuration = 0;
        this.lastChecksum = 0;
        this.lastCount = 0;
        this.stream = stream;

        this.insertionQueue = [];
        this.deletionQueue = [];
    }

    async run() {
        this.insertionQueue.forEach((quad) => {
            this.store.addQuad(quad);
        });
        this.deletionQueue.forEach((quad) => {
            this.store.removeQuad(quad);
        });
        this.insertionQueue = [];
        this.deletionQueue = [];

        const start = process.hrtime.bigint();
        let latest = start;
        // observing the changes
        new Promise(() => {
            this.stream.on('data', (binding) => {
                latest = process.hrtime.bigint();
                if (isAddition(binding)) {
                    ++this.lastCount;
                    this.total.set(binding, (this.total.get(binding) || 0) + 1)
                } else {
                    --this.lastCount;
                    this.total.set(binding, (this.total.get(binding) || 0) - 1)
                }
            });
        });
        // wait for 2 seconds of inactivity
        await dynamic_timeout(() => Number(latest - process.hrtime.bigint()) / 1_000_000 + 2000);
        // cleaning up
        this.stream.removeAllListeners('data');
        this.lastDuration = Number(latest - start) / 1_000_000;
        this.lastChecksum = get_checksum_for_resultset(this.total);
    }

    getLastDuration() {
        return this.lastDuration;
    }

    getLastChecksum() {
        return this.lastChecksum;
    }

    getLastCount() {
        return this.lastCount;
    }

    createNamedNode(uri) {
        return DataFactory.namedNode(uri);
    }

    createBlankNode(id) {
        return DataFactory.blankNode(`${id}`);
    }

    createTypedLiteralNode(value, dtype) {
        return DataFactory.literal(value, DataFactory.namedNode(dtype));
    }

    createLangLiteralNode(value, lang) {
        return DataFactory.literal(value, lang);
    }

    insertQuad(s, p, o) {
        this.insertionQueue.push(DataFactory.quad(s, p, o));
    }

    removeQuad(s, p, o) {
        this.deletionQueue.push(DataFactory.quad(s, p, o));
    }

}

export async function create(query) {
    const engine = new incremunica.QueryEngine();
    const store = new StreamingStore();
    const stream = await engine.queryBindings(query, { sources: [store] });
    const evaluator = new IncremunicaEvaluator(stream, store);
    return evaluator;
}
