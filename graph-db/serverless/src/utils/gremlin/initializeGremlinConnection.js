import gremlin from 'gremlin'

const { DriverRemoteConnection } = gremlin.driver
const { Graph } = gremlin.structure

let connection
let driverRC

export const closeGremlinConnection = () => {
  if (driverRC) {
console.log("-------closeGremlinConnection------")

//     return driverRC.close().then(() => {
//       driverRC = null
//       connection = null
// console.log("!!!!!!closeGremlinConnection!!!!!!")
//
//     })
Promise.resolve(driverRC.close())
    driverRC = null
    connection = null
console.log("!!!!!!closeGremlinConnection!!!!!!")

  }
}

/**
 * Connects to gremlin server to give reusable connection
 * @returns Gremlin traversal interface
 */
export const initializeGremlinConnection = () => {
  if (connection) {
    return connection
  }

  const gremlinUrl = process.env.GREMLIN_URL

  if (!driverRC) {
    driverRC = new DriverRemoteConnection(gremlinUrl, {})
  }

  const graph = new Graph()
  connection = graph.traversal().withRemote(driverRC)
console.log("=======initializeGremlinConnection=====")
  return connection
}
