$(function() {
    // add a click handler to the button
    $("#submitClaimButton").click(function(event) {
        // make an ajax get request to get the message
        jsRoutes.controllers.ClaimController.submitClaim($("#claimXml").val()).ajax({

            success: function(data) {
                $("#claimResults").val(data.value)
            }
        })
    })
})