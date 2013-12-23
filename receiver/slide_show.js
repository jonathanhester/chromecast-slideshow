$(function() {
	var delay;
	var galleria;

	Galleria.ready(function() {
		galleria = this;
		galleria.enterFullscreen();
	});

	Galleria.loadTheme('galleria.classic.min.js');

	function isTesting() {
		return (window.location.search.indexOf("test") > -1)
	}
	if (isTesting()) {
		/*
		 * http://jonathanhester.com/1920x1080-famous-paintings-sunday-afternoon-hd-widescreen-high-definition-wallpaper.jpg
		 * http://www.frozy.net/wp-content/uploads/2012/07/Yosemite-Valley-snow.jpg
		 * http://www.venusbuzz.com/wp-content/uploads/48_web32.jpeg
		 * http://www.hdwallpaperscool.com/wp-content/uploads/2013/11/mountain-nature-hd-wallpapers-top-beautiful-desktop-nature-images-background.jpg
		 */
		
		/* or a picasa album
		 * 115857411259674695117/RevolverWorlds2010
		 */
		$(".test-controls").show();
		$(".messages").hide();
		var prevButton = $("#prevButton");
		var nextButton = $("#nextButton");
		var playButton = $("#playButton");
		prevButton.attr("disabled", "disabled");
		nextButton.attr("disabled", "disabled");
		prevButton.click(function() {
			onMessage({
				message : {
					type : "previous"
				}
			});
		});
		nextButton.click(function() {
			onMessage({
				message : {
					type : "next"
				}
			});
		});
		playButton.click(function() {
			var images;
			if ($("#imagesText").val())
				images = $("#imagesText").val().split("\n");
			var delay = $("#delay").val();
			prevButton.removeAttr("disabled");
			nextButton.removeAttr("disabled");
//			playButton.attr("disabled", "disabled");
			var extra = {};
			if (images && images.length > 0) {
				extra = {
					images : images
				};
			} else if ($("#picasaAlbum").val()) {
				extra = {
					picasa : $("#picasaAlbum").val()
				}
			}
			onMessage({
				message : $.extend({
					type : "queue",
					delay : delay
				}, extra)
			});
		});

	} else {
		var receiver = new cast.receiver.Receiver(
				'bc28e054-e01e-474f-a798-d73f6e62688b', [ 'HelloWorld' ], "", 5), channelHandler = new cast.receiver.ChannelHandler(
				'HelloWorld'), $messages = $('.messages'), $body = $('body');
		channelHandler.addChannelFactory(receiver
				.createChannelFactory('HelloWorld'));
		receiver.start();
		channelHandler.addEventListener(
				cast.receiver.Channel.EventType.MESSAGE, onMessage.bind(this));
	}

	function onMessage(event) {
		console.log(event.message);
		if (event.message.type == "queue") {
			delay = event.message.delay;
			var runOptions;
			if (event.message.images) {
				runOptions = createDataWithUrls(event.message.images)
			} else if (event.message.picasa) {
				runOptions = {
					picasa : 'useralbum:' + event.message.picasa
				};
			}
			play(runOptions, delay);
		} else if (galleria) {
			if (event.message.type == "pause") {
				galleria.pause();
			} else if (event.message.type == "play") {
				galleria.play(delay);
			} else if (event.message.type == "previous") {
				galleria.prev();
				galleria.setPlaytime(delay);
			} else if (event.message.type == "next") {
				galleria.next();
				galleria.setPlaytime(delay);
			}

		}
	}

	function createDataWithUrls(images) {
		var data = [];
		$.each(images, function(i, image) {
			data.push({
				thumb : image,
				image : image,
				big : image
			})
		});
		return {
			dataSource : data
		};
	}

	function createDataWithPicasa() {

	}

	function queueImage(image) {
		var img = $('<img >'); // Equivalent: $(document.createElement('img'))
		img.attr('src', image);
		img.appendTo('.galleria');
	}

	function clearImages() {

	}

	var globalOptions = {
		showImagenav : false,
		thumbnails : false,
		transition : 'fade',
		fullscreenCrop : false,
		transitionSpeed : 1000,
	}

	function play(runOptions, delay) {
		var data = [];
		var options = $.extend(globalOptions, {
			autoplay : parseInt(delay)
		});
		$.extend(runOptions, options);

		$(".messages").hide();
		Galleria.run('.galleria', runOptions);

		Galleria.on('progress', function(e) {
		});
	}
	window.onMessage = onMessage;
});
