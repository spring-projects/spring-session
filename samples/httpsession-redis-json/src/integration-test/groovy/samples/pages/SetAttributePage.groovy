package samples.pages

import geb.Page

/**
 * @author jitendra on 15/3/16.
 */
class SetAttributePage extends Page {
	static url="/setValue"

	static at=
	{
        title == "Spring Session Sample - Home"
    }

	static content=
	{
        tdKey { $('td') }
    }
}
