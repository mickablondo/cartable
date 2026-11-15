package dev.mickablondo.cartable.sheet;

public class NoCoursePassagesFoundException extends RuntimeException {

	public NoCoursePassagesFoundException() {
		super("Aucun passage de cours n'a été trouvé pour ce thème et cette matière. "
				+ "Importez ou indexez d'abord un cours correspondant.");
	}

}
