package id.co.erdigma.satudata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Provider dipin LOCAL supaya pemuatan konteks tidak pernah menyentuh AWS —
 * tidak butuh kredensial, tidak butuh jaringan, dan tidak melambat.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
class SatudataApplicationTests {

	@Test
	void contextLoads() {
	}

}
