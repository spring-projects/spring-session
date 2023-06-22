'use strict'

module.exports.register = function () {
  this.once('contentAggregated', ({ contentAggregate }) => {
    contentAggregate.forEach((componentVersionBucket) => {
      Object.assign(componentVersionBucket, { name: 'session', display: 'Spring Session', title: 'Spring Session' })
    })
  })
}
