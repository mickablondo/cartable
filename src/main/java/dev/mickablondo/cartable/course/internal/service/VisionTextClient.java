package dev.mickablondo.cartable.course.internal.service;

interface VisionTextClient {

	String transcribe(String prompt, byte[] image, String mediaType);

}
