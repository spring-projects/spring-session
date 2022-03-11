'use strict'

module.exports.register = function({ config }) {

    this.on('contentAggregated', ({ contentAggregate }) => {
        contentAggregate.forEach(aggregate => {
            if (aggregate.version === "2.6.2" &&
                aggregate.prerelease == "-SNAPSHOT") {
                aggregate.version = "2.6.2"
                aggregate.displayVersion = `${aggregate.version}`
                delete aggregate.prerelease
            }
        })
    })
}
