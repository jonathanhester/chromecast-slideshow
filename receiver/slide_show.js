$(function() {
	var delay;
	var galleria;

	Galleria.ready(function() {
		galleria = this;
		galleria.enterFullscreen();
	});

	Galleria.loadTheme('galleria.classic.min.js');

	var receiver = new cast.receiver.Receiver(
			'bc28e054-e01e-474f-a798-d73f6e62688b', [ 'HelloWorld' ], "", 5), channelHandler = new cast.receiver.ChannelHandler(
			'HelloWorld'), $messages = $('.messages'), $body = $('body');

	channelHandler.addChannelFactory(receiver
			.createChannelFactory('HelloWorld'));

	receiver.start();

	channelHandler.addEventListener(cast.receiver.Channel.EventType.MESSAGE,
			onMessage.bind(this));

	function onMessage(event) {
		console.log(event.message);
		if (event.message.type == "queue") {
			delay = event.message.delay;
			play(event.message.images);
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

	function queueImage(image) {
		var img = $('<img >'); // Equivalent: $(document.createElement('img'))
		img.attr('src', image);
		img.appendTo('.galleria');
	}

	function clearImages() {

	}

	function play(images) {
		var data = [];
		$.each(images, function(i, image) {
			data.push({
				thumb : image,
				image : image,
				big : image
			})
		});
		var options = {
			autoplay : delay,
			showImagenav : false,
			thumbnails : false,
			transition : 'fade',
			fullscreenCrop : false,
			transitionSpeed : 1000,
		}
		$(".messages").hide();
		Galleria.configure(options);
		Galleria.run('.galleria', {dataSource : data});

		Galleria.on('progress', function(e) {
		});

	}
	window.onMessage = onMessage;
});