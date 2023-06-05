const {CosmosClient} = require("@azure/cosmos");
require('dotenv').config()
const {performance} = require('perf_hooks');

const endpoint = process.env.ENDPOINT;
const key = process.env.KEY;
const client = new CosmosClient({endpoint, key});

async function main() {
    const top = process.argv[2] ?? 100;
    console.log(`Max Elements to update: ${top}`);

    const {database} = await client.databases.createIfNotExists({id: 'db'});
    console.log(`Database found: ${database.id}`)

    const {container} = await database.containers.createIfNotExists({id: 'biz-events'});
    console.log(`Container found: ${container.id}`)

    const queryOptions = {
        maxItemCount: 2000, // page size
    }
    const iterator = await container.items
        .query("SELECT * FROM c WHERE c.eventStatus = 'FAILED' AND IS_DEFINED(c.idPaymentManager) AND STARTSWITH(c.eventErrorMessage, 'Error 404 ') OFFSET 0 LIMIT " + top, queryOptions);

    let pageNumber = 0;
    let startTime = performance.now()
    while (iterator.hasMoreResults()) {
        let page = await iterator.fetchNext();
        if ((page.resources === undefined) || (page.resources.length === 0)) {
            break;
        }
        console.log(`Found ${page.resources.length} items in page number ${pageNumber}`);
        let counter = 0;
        for (const elem of page.resources) {
            elem.idPaymentManager = undefined;
            elem.eventStatus = "RETRY";
            await container.items.upsert(elem);
            counter++;
            if (counter % 100 === 0) {
                let progress = counter / page.resources.length * 100;
                console.log(`Page ${pageNumber} progress... ${progress}%`);
            }
        }
        pageNumber++;
    }
    let endTime = performance.now()
    console.log(`Done in ${(endTime - startTime) / 1000 / 60} minutes!`)
}

main().catch((error) => {
    console.error(error);
});
