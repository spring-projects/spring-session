'use strict'

module.exports.register = function () {
  this.once('contentAggregated', ({ contentAggregate }) => {
    contentAggregate.forEach((componentVersionBucket) => {
      if (componentVersionBucket.prerelease === 'true') componentVersionBucket.prerelease = true
      if (typeof componentVersionBucket.prerelease === 'string' && componentVersionBucket.prerelease !== '-SNAPSHOT') {
        componentVersionBucket.version += componentVersionBucket.prerelease
        componentVersionBucket.prerelease = true
      }
    })
  })
}
