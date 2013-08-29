Dropzone.autoDiscover = false;
(function () {
    var upload = document.getElementById("upload"),
        clear = document.getElementById("clear"),
        aetField = document.getElementById("aet"),
        dropzone = new Dropzone(".dropzone", {
            url: "rs/stow/DCM4CHEE/studies",
            uploadMultiple: true,
            autoProcessQueue: false,
            addRemoveLinks: true,
            parallelUploads: 1000
//            previewTemplate: "<div class=\"dz-preview dz-file-preview\">\n  <div class=\"dz-details\">\n    <div class=\"dz-filename\"><span data-dz-name></span></div>\n    <div class=\"dz-size\" data-dz-size></div>\n    <img data-dz-thumbnail />\n  </div>\n  <div class=\"dz-progress\"><span class=\"dz-upload\" data-dz-uploadprogress></span></div>\n  <div class=\"dz-success-mark\"><span>✔</span></div>\n  <div class=\"dz-error-mark\"><span>✘</span></div>\n  <div class=\"dz-error-message\"><iframe data-dz-errormessage></iframe></div>\n</div>"
        });
    
    upload.onclick = function () {
        dropzone.options.url = "rs/stow/" + aetField.value + "/studies";
        dropzone.processQueue();
    };
    clear.onclick = function () {
        dropzone.removeAllFiles(false);
    };
    
}());
